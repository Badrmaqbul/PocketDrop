<#
  Builds dist\PocketDrop-<version>-alpha.exe  (a normal Windows installer with Java and JavaFX inside).

  Needs, on THIS computer, only while building:
    - A recent JDK, 24 or newer (jpackage in recent JDKs supports WiX 5/6)     - Maven (mvn)
    - WiX Toolset 6 (free, currently maintained) with the UI and Util extensions:
        winget install wixtoolset.wix
        wix extension add -g WixToolset.UI.wixext/6.0.0
        wix extension add -g WixToolset.Util.wixext/6.0.0
      (WiX 3.14 reached end of life in early 2025 and is no longer used.)
  The people who install PocketDrop need nothing.

  Usage:   powershell -ExecutionPolicy Bypass -File scripts\build-installer.ps1
           add  -Sign  to sign the installer (see scripts\sign-installer.ps1)
#>
param([switch]$Sign)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# Keep this in sync with <version> in pom.xml. It must stay plain numeric (x.y.z):
# the Windows installer format rejects pre-release suffixes like "-alpha".
$version = '0.1.0'
$releaseName = "PocketDrop-$version-alpha"   # the file name people download from GitHub
foreach ($tool in 'mvn', 'jpackage', 'java') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "'$tool' was not found on PATH." }
}
if (-not (Get-Command 'candle.exe' -ErrorAction SilentlyContinue) -and -not (Get-Command 'wix.exe' -ErrorAction SilentlyContinue)) {
    Write-Warning "WiX Toolset was not found on PATH. Install WiX Toolset 6: 'winget install wixtoolset.wix', then add the UI and Util extensions ('wix extension add -g WixToolset.UI.wixext/6.0.0' and 'wix extension add -g WixToolset.Util.wixext/6.0.0') and open a new terminal."
}

Write-Host '1/4  Building the jar...'
mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

Write-Host '2/4  Collecting JavaFX...'
mvn -q dependency:copy-dependencies "-DoutputDirectory=target/libs" -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) { throw 'Could not copy the JavaFX files.' }

$appIn = 'target\jpackage-input'
Remove-Item -Recurse -Force $appIn -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $appIn | Out-Null
Copy-Item "target\pocketdrop-$version.jar" "$appIn\pocketdrop.jar"

# JavaFX goes in its own folder (the module path); PocketDrop has no other libraries
Remove-Item -Recurse -Force target\jfx -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path target\jfx | Out-Null
Get-ChildItem target\libs -Filter 'javafx-*.jar' | ForEach-Object { Copy-Item $_.FullName target\jfx }

# Java modules the app needs (computed from the jars, plus the ones we know are needed)
$modules = @('java.base', 'java.desktop', 'java.net.http', 'java.logging', 'java.naming',
             'jdk.unsupported', 'javafx.base', 'javafx.graphics', 'javafx.controls')
$allJars = (Get-ChildItem $appIn -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
$found = & jdeps --multi-release 21 --ignore-missing-deps --print-module-deps -cp $allJars "$appIn\pocketdrop.jar" 2>$null
if ($LASTEXITCODE -eq 0 -and $found) { $modules += ($found -split ',') }
$available = & java --list-modules
if ($available -match '^jdk\.crypto\.ec') { $modules += 'jdk.crypto.ec' }
$modules = $modules | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique

Write-Host '3/4  Creating the installer (this takes a minute)...'
Remove-Item -Recurse -Force dist -ErrorAction SilentlyContinue
& jpackage `
    --type exe `
    --name 'PocketDrop Alpha' `
    --app-version $version `
    --vendor 'Dev.Badr' `
    --description 'Saves what you send to your private Telegram bot on this computer.' `
    --input $appIn `
    --main-jar pocketdrop.jar `
    --main-class dev.badr.pocketdrop.ui.Launcher `
    --module-path target\jfx `
    --add-modules ($modules -join ',') `
    --icon assets\icon.ico `
    --win-menu --win-shortcut --win-dir-chooser --win-per-user-install `
    --win-upgrade-uuid c1842489-236c-45b5-91d9-27379bb80e11 `
    --java-options '--enable-native-access=javafx.graphics' `
    --dest dist
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

# The installer itself is versioned 0.1.0 (numeric only); "-alpha" is part of the
# public file name and the GitHub tag, not of the internal Windows version.
Get-ChildItem "dist\PocketDrop*-$version.exe" | Rename-Item -NewName "$releaseName.exe"

Write-Host '4/4  Done.'
Get-ChildItem dist | ForEach-Object { Write-Host ("     " + $_.FullName) }

if ($Sign) { & "$PSScriptRoot\sign-installer.ps1" }
