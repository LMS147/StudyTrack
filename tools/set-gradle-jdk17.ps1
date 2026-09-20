# StudyTrack — point Gradle at JDK 17
#
# Why: Gradle 8.7 (this project's wrapper) runs on Java 8-21 only. If the
# machine's default `java` is newer (Java 25 needs Gradle 9.1+), every build
# stops with:
#
#   * What went wrong:
#   25.0.1
#
# That bare version number is the JDK Gradle refuses to run on - the project
# itself is fine (CI builds it on JDK 17).
#
# What this script does: finds an installed JDK 17 and writes
# org.gradle.java.home into the machine-local ~/.gradle/gradle.properties,
# which is where Gradle looks for it. Nothing in the repository is touched.
#
# Run it normally (do NOT dot-source it):
#   .\tools\set-gradle-jdk17.ps1
#
# If Windows blocks it with "running scripts is disabled on this system",
# bypass the policy for this one run:
#   powershell -ExecutionPolicy Bypass -File .\tools\set-gradle-jdk17.ps1
#
# If no JDK 17 is installed, the script says how to get one and stops.

$ErrorActionPreference = 'Stop'

$candidates = @(
    'C:\Program Files\Java',
    'C:\Program Files\Eclipse Adoptium',
    'C:\Program Files\Microsoft',
    'C:\Program Files\Amazon Corretto',
    (Join-Path $env:USERPROFILE '.jdks')          # where Android Studio's "Download JDK" puts them
)

$found = @()
foreach ($root in $candidates) {
    if (-not (Test-Path $root)) { continue }
    foreach ($dir in Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue) {
        $javaExe = Join-Path $dir.FullName 'bin\java.exe'
        if (-not (Test-Path $javaExe)) { continue }
        $versionText = (& $javaExe -version 2>&1 | Out-String)
        if ($versionText -match 'version "17\.') { $found += $dir.FullName }
    }
}

if ($found.Count -eq 0) {
    Write-Host ''
    Write-Host 'No JDK 17 found on this machine. Install one, then re-run this script:' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Android Studio (no admin rights needed):'
    Write-Host '    Settings > Build, Execution, Deployment > Build Tools > Gradle'
    Write-Host '    > Gradle JDK > Download JDK... > Version 17 > Download'
    Write-Host ''
    Write-Host '  or from a terminal:'
    Write-Host '    winget install EclipseAdoptium.Temurin.17.JDK'
    Write-Host ''
    exit 1
}

$jdk = $found[0]
Write-Host "Found JDK 17: $jdk" -ForegroundColor Green

$propsDir  = Join-Path $env:USERPROFILE '.gradle'
$propsFile = Join-Path $propsDir 'gradle.properties'
New-Item -ItemType Directory -Force -Path $propsDir | Out-Null

$lines = @()
if (Test-Path $propsFile) {
    Copy-Item -Path $propsFile -Destination "$($propsFile).bak" -Force
    # Drop any previous setting so the file stays idempotent.
    $lines = @(Get-Content -Path $propsFile | Where-Object { $_ -notmatch '^\s*org\.gradle\.java\.home\s*=' })
    Write-Host "Backed up the previous file to $($propsFile).bak"
}

$lines += 'org.gradle.java.home=' + ($jdk -replace '\\', '/')
Set-Content -Path $propsFile -Value $lines -Encoding ASCII

Write-Host ''
Write-Host "Wrote $propsFile :" -ForegroundColor Green
Get-Content -Path $propsFile | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host 'Now run:  .\gradlew assembleDebug' -ForegroundColor Green
Write-Host 'Note: Android Studio reports a warning when both this property and its' -ForegroundColor DarkGray
Write-Host 'own "Gradle JDK" setting are present - the property wins. Keep both on 17.' -ForegroundColor DarkGray
