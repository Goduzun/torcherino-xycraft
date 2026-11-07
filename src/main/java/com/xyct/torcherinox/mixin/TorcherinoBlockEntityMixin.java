package com.xyct.torcherinox.mixin;

import com.xyct.torcherinox.XyAccel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import torcherino.block.entity.TorcherinoBlockEntity;

/**
 * Torcherino walks every block position inside the torch's field each tick and calls
 * {@code tickBlock(pos)} for it. Vanilla blocks get random ticks plus their BlockEntityTicker;
 * XyCraft blocks have neither, so nothing happens there. After Torcherino is done with a
 * position we hand the same position to XyCraft's tick system {@code speed} times, which makes
 * the torch's own speed slider drive XyCraft machines.
 */
@Mixin(TorcherinoBlockEntity.class)
public abstract class TorcherinoBlockEntityMixin {

    @Shadow
    private int speed;

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void torcherinoxycraft$driveXyCraftMachines(BlockPos pos, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(pos);
        XyAccel.boost(serverLevel, pos, state, this.speed);
    }
}
