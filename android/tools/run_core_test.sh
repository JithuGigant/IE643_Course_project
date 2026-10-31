#!/bin/sh
# Usage: tools/run_core_test.sh <kotlinc> <vector dir> <plausibility.bin>
set -e
OUT=$(mktemp -d)
"$1" app/src/main/java/com/ie643/cxr/core/Prep.kt app/src/main/java/com/ie643/cxr/core/Checks.kt tools/CoreTest.kt -d "$OUT/c.jar"
java -cp "$OUT/c.jar:$(dirname "$1")/../lib/kotlin-stdlib.jar" CoreTestKt "$2" "$3"
