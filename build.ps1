# Builds torcherino_xycraft without Gradle.
# Only a JDK 21 and an installed NeoForge 1.21.1 instance are required.
param(
    [string]$Instance = "E:\mc\.minecraft\versions\PlaytoDeath ExtremeCareers",
    [string]$Libraries = "E:\mc\.minecraft\libraries",
    [string]$NeoForgeDir = "21.1.256",
    [string]$Jdk = "C:\Users\God\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "build\classes"
$jarOut = Join-Path $root "build\torcherino_xycraft-1.0.0.jar"

$nf = Join-Path $Libraries "net\neoforged\neoforge\$NeoForgeDir"
$mc = Get-ChildItem (Join-Path $Libraries "net\minecraft\client") -Directory |
    Where-Object { $_.Name -like "1.21.1-*" } | Select-Object -First 1
$loader = Get-ChildItem (Join-Path $Libraries "net\neoforged\fancymodloader\loader") -Directory |
    Sort-Object Name | Select-Object -Last 1
$mixin = (Get-ChildItem (Join-Path $Libraries "org\spongepowered\mixin") -Directory |
    Sort-Object Name | Select-Object -Last 1).FullName
$mixinJar = Get-ChildItem $mixin -Filter "mixin-*.jar" | Select-Object -First 1

$mods = Join-Path $Instance "mods"
$cp = @(
    (Join-Path $nf "neoforge-$NeoForgeDir-client.jar"),
    (Join-Path $mc.FullName (Get-ChildItem $mc.FullName -Filter "*.jar" | Select-Object -First 1 -ExpandProperty Name)),
    (Join-Path $nf "neoforge-$NeoForgeDir-universal.jar"),
    (Join-Path $loader.FullName ("loader-" + $loader.Name + ".jar")),
    $mixinJar.FullName,
    (Get-ChildItem $mods -Filter "xycraft_core-*.jar" | Select-Object -First 1).FullName,
    (Get-ChildItem $mods -Filter "xycraft_machines-*.jar" | Select-Object -First 1).FullName,
    (Get-ChildItem $mods -Filter "*Torcherino*.jar" | Select-Object -First 1).FullName
) -join ';'

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null

$srcs = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
& (Join-Path $Jdk "javac.exe") -encoding UTF-8 -proc:none -nowarn -d $out -cp $cp $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item (Join-Path $root "src\main\resources\*") -Destination $out -Recurse -Force
Remove-Item $jarOut -Force -ErrorAction SilentlyContinue
Push-Location $out
& (Join-Path $Jdk "jar.exe") cf $jarOut .
Pop-Location

Write-Host "built: $jarOut"
