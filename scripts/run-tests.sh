#!/bin/sh
# Same as run-tests.ps1 for Linux/macOS.
cd "$(dirname "$0")/.." || exit 1
mvn -q test-compile || exit 1
CP="target/classes:target/test-classes"
java -cp "$CP" dev.badr.pocketdrop.AllTests || exit 1
java -cp "$CP" dev.badr.pocketdrop.SecurityChecks || exit 1
java -cp "$CP" dev.badr.pocketdrop.ProductionPathCheck
