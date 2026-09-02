#!/bin/bash
# Build every addon in src/ into a jar.
#
# Needs a JDK 8, the server jar, and InfiniteLoader.jar (for infinite.api.*). Mixin is fetched
# once into lib/ if it is not already there.
#
#   ./build-all.sh                     builds the live addons
#   ./build-all.sh hamfix moderncmds   builds named ones, including retired
set -euo pipefail
cd "$(dirname "$0")"

JDK=${JDK:-/usr/lib/jvm/temurin-8-jdk-amd64/bin}
MIXIN_VER=0.8.5

[ -f server.jar ] || { echo "ERROR: copy the server jar here as server.jar"; exit 1; }
[ -f InfiniteLoader.jar ] || { echo "ERROR: copy InfiniteLoader.jar here too"; exit 1; }

mkdir -p lib
if [ ! -f lib/mixin.jar ]; then
   echo "==> fetching mixin $MIXIN_VER"
   curl -fsSL -o lib/mixin.jar \
      "https://repo1.maven.org/maven2/org/spongepowered/mixin/$MIXIN_VER/mixin-$MIXIN_VER.jar"
fi

CP="server.jar:InfiniteLoader.jar:lib/mixin.jar"
MODS=("$@")
if [ ${#MODS[@]} -eq 0 ]; then
   MODS=(worldprotect landclaim perms anticheat)
fi

for mod in "${MODS[@]}"; do
   dir="src/$mod"
   [ -d "$dir" ] || dir="src/retired/$mod"
   [ -d "$dir" ] || { echo "ERROR: no source for $mod"; exit 1; }

   echo "==> building $mod"
   rm -rf "build/$mod"; mkdir -p "build/$mod"

   # -proc:none is NOT optional: Mixin's annotation processor assumes an obfuscated game and
   # fails on this one. It is the first thing that breaks a rebuild from source.
   "$JDK/javac" -source 8 -target 8 -proc:none -nowarn -encoding UTF-8 \
      -cp "$CP" -d "build/$mod" $(find "$dir/src" -name '*.java')

   [ -d "$dir/resources" ] && cp -r "$dir/resources/." "build/$mod/"
   ( cd "build/$mod" && "$JDK/jar" cf "../../mods/$mod-0.1.0.jar" . )
   echo "    mods/$mod-0.1.0.jar"
done

echo
echo "Done. Copy mods/*.jar into the server's mods/ folder and restart."
