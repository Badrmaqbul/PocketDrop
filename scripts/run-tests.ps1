# Runs all automatic tests (no internet needed: the bot is tested against a pretend Telegram server).
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
mvn -q test-compile
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$cp = "target\classes;target\test-classes"

Write-Host '--- bot tests ---'
java -cp $cp dev.badr.pocketdrop.AllTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host '--- security checks (certificates, redirects, token leaks, file paths; needs keytool from the JDK) ---'
java -cp $cp dev.badr.pocketdrop.SecurityChecks
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host '--- real addresses (optional, needs an administrator terminal) ---'
java -cp $cp dev.badr.pocketdrop.ProductionPathCheck
exit $LASTEXITCODE
