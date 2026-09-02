# Build all three addons. Run from the infinite-plugins folder on your PC.
# Needs your JDK 8 and the server jar.
$ErrorActionPreference = "Stop"
$JDK = "C:\Program Files\Java\jdk1.8.0_351\bin"
$MIXIN_VER = "0.8.5"

if (-not (Test-Path "server.jar")) {
  throw "Copy minecraft-infinite-server.jar into this folder as server.jar first."
}
if (-not (Test-Path "InfiniteLoader.jar")) {
  throw "Copy InfiniteLoader.jar into this folder too (needed for infinite.api.*)."
}
if (-not (Test-Path "lib\mixin.jar")) {
  New-Item -ItemType Directory -Force -Path lib | Out-Null
  Write-Host "downloading mixin $MIXIN_VER ..."
  Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/spongepowered/mixin/$MIXIN_VER/mixin-$MIXIN_VER.jar" -OutFile "lib\mixin.jar"
}

$CP = "server.jar;InfiniteLoader.jar;lib\mixin.jar"

foreach ($mod in @("hamfix","landclaim","moderncmds")) {
  Write-Host "`n=== building $mod ==="
  Remove-Item -Recurse -Force "$mod\build" -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path "$mod\build" | Out-Null

  $files = (Get-ChildItem -Recurse -Filter *.java "$mod\src").FullName
  # -proc:none is required: MODDING.md, Mixin's AP assumes an obfuscated game
  & "$JDK\javac.exe" -source 8 -target 8 -proc:none -nowarn -cp $CP -d "$mod\build" $files
  if ($LASTEXITCODE -ne 0) { throw "$mod failed to compile" }

  Copy-Item -Recurse -Force "$mod\resources\*" "$mod\build\"
  & "$JDK\jar.exe" cf "$mod-0.1.0.jar" -C "$mod\build" .
  Write-Host "BUILT: $mod-0.1.0.jar"
}
Write-Host "`nAll three built. Put the jars in your sandbox mods\ folder."
