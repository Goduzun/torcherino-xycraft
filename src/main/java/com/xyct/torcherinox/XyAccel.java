package com.xyct.torcherinox;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import tv.soaryn.xycraft.core.content.attachments.accessors.Ticks;
import tv.soaryn.xycraft.core.content.blocks.XyBlock;
import tv.soaryn.xycraft.core.content.systems.BlockTickSystemLevelAttachment;
import tv.soaryn.xycraft.core.content.systems.ServerBlockTickSystem;

/**
 * Drives XyCraft's own block tick systems for a single position.
 *
 * <p>XyCraft machines are not ticked through {@code Block#getTicker}, they are ticked by
 * {@code ServerBlockTickSystem#postTick} once per level tick, and their progress is stored as a
 * per-block-entity countdown ({@code CoreAttachments.ProcessTime}) that is decremented by exactly
 * one per {@code tickBlocks(...)} call. Replaying {@code tickBlocks(...)} therefore speeds the
 * machine up linearly, which is what a Torcherino torch asks us to do.</p>
 */
public final class XyAccel {

    private static final MethodType TICK_BLOCKS_TYPE = MethodType.methodType(
            void.class,
            ServerLevel.class,
            BlockTickSystemLevelAttachment.class,
            BlockPos.MutableBlockPos.class,
            BlockState.class,
            long.class);

    private static final Map<Class<?>, MethodHandle[]> HANDLES = new ConcurrentHashMap<>();

    private static volatile Field systemField;
    private static volatile boolean fieldLookupDone;
    private static volatile boolean disabled;

    private XyAccel() {
    }

    /** Safety valve: a single position never replays more than this many machine ticks per torch tick. */
    private static final int MAX_TIMES = 64;

    public static void boost(ServerLevel level, BlockPos pos, BlockState state, int times) {
        if (disabled || times <= 0) {
            return;
        }
        if (times > MAX_TIMES) {
            times = MAX_TIMES;
        }
        try {
            if (!(state.getBlock() instanceof XyBlock block)) {
                return;
            }
            Field field = systemField();
            if (field == null) {
                return;
            }

            Object holder = field.get(block);
            if (!(holder instanceof DeferredHolder<?, ?> deferred)) {
                return;
            }

            Object system;
            try {
                system = deferred.get();
            } catch (Throwable notReadyYet) {
                return;
            }
            if (!(system instanceof ServerBlockTickSystem tickSystem)) {
                return;
            }

            BlockTickSystemLevelAttachment data =
                    (BlockTickSystemLevelAttachment) tickSystem.getData(level);
            if (data == null) {
                return;
            }

            BlockPos.MutableBlockPos cursor =
                    new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            if (!tickSystem.isValidState(level, state, cursor)) {
                return;
            }

            MethodHandle[] handles = handles(tickSystem.getClass());
            if (handles == null) {
                return;
            }

            long time = Ticks.of(level);
            long frequency = data.frequency();
            boolean batchCycle = frequency > 0L && (pos.asLong() + time) % frequency == 0L;

            for (int i = 0; i < times; i++) {
                handles[0].invoke(tickSystem, level, data, cursor, state, time);
                if (batchCycle) {
                    handles[1].invoke(tickSystem, level, data, cursor, state, time);
                }
            }
        } catch (Throwable t) {
            // Never let a compat patch take the server down; if the internals moved, stay quiet.
            disabled = true;
        }
    }

    private static Field systemField() {
        if (!fieldLookupDone) {
            synchronized (XyAccel.class) {
                if (!fieldLookupDone) {
                    try {
                        Field f = XyBlock.class.getDeclaredField("_system");
                        f.setAccessible(true);
                        systemField = f;
                    } catch (Throwable t) {
                        systemField = null;
                    }
                    fieldLookupDone = true;
                }
            }
        }
        return systemField;
    }

    private static MethodHandle[] handles(Class<?> type) {
        MethodHandle[] cached = HANDLES.get(type);
        if (cached != null) {
            return cached[0] == null ? null : cached;
        }
        MethodHandle tickBlocks = lookup(type, "tickBlocks");
        MethodHandle tickBatch = lookup(type, "tickBatchBlocks");
        MethodHandle[] value = new MethodHandle[] {tickBlocks, tickBatch};
        if (tickBlocks == null || tickBatch == null) {
            HANDLES.put(type, new MethodHandle[] {null, null});
            return null;
        }
        HANDLES.put(type, value);
        return value;
    }

    private static MethodHandle lookup(Class<?> type, String name) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            return lookup.findVirtual(type, name, TICK_BLOCKS_TYPE);
        } catch (Throwable ignored) {
            // fall through to plain reflection
        }
        try {
            var method = type.getDeclaredMethod(name,
                    ServerLevel.class,
                    BlockTickSystemLevelAttachment.class,
                    BlockPos.MutableBlockPos.class,
                    BlockState.class,
                    long.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
