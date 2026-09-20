# StudyTrack — point Gradle at JDK 17
#
# Why: Gradle versions below 9.0.0 cannot run on a JVM 25 or newer (the Kotlin
# version they embed cannot read it — fixed in Kotlin 2.1.20). The build stops
# with an error whose entire message is the version number:
#
#   * What went wrong:
#   25.0.1
#
# That number is the JVM, not your code: CI builds this project on JDK 17.
#
# What this script does: finds an installed JDK 17 and writes
# org.gradle.java.home into the machine-local ~/.gradle/gradle.properties,
# which is where Gradle looks for it. Nothing in the repository is touched.
#
# Run it normally (do NOT dot-source it):
#   .\tools\set-gradle-jdk17.ps1
#
# If Windows blocks scripts: powershell -ExecutionPolicy Bypass -File .\tools\set-gradle-jdk17.ps1
# Point it at a specific JDK:  .\tools\set-gradle-jdk17.ps1 -JdkPath "C:\Users\me\.jdks\temurin-17.0.11"
# Also set JAVA_HOME for the user: .\tools\set-gradle-jdk17.ps1 -SetJavaHome

[CmdletBinding()]
param(
    [string]$JdkPath,
    [switch]$SetJavaHome
)

# NOTE: deliberately NOT 'Stop'. Probing a JDK writes its version to stderr, and
# on Windows PowerShell 5.1 that becomes a NativeCommandError which would abort
# this script mid-scan (it did, once). Errors are checked explicitly instead.
$ErrorActionPreference = 'Continue'

function Get-JdkVersion {
    param([string]$Home)

    # Prefer the `release` file next to the JDK: no process, no stderr, no
    # PowerShell error-stream weirdness.
    $release = Join-Path $Home 'release'
    if (Test-Path -LiteralPath $release) {
        $raw = Get-Content -LiteralPath $release -Raw -ErrorAction SilentlyContinue
        if ($raw -match 'JAVA_VERSION\s*=\s*"([^"]+)"') { return $Matches[1] }
    }

    # Fallback: run java through cmd so stderr is redirected by cmd, not PowerShell.
    $exe = Join-Path $Home 'bin\java.exe'
    if (Test-Path -LiteralPath $exe) {
        $out = cmd /c "`"$exe`" -version 2>&1"
        if ("$out" -match 'version "([^"]+)"') { return $Matches[1] }
    }
    return $null
}

function Get-CandidateHomes {
    $roots = @(
        (Join-Path $env:USERPROFILE '.jdks'),                       # Android Studio's "Download JDK"
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'), # Android Studio's bundled runtime
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu',
        'C:\Program Files\BellSoft'
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        # The path may itself be a JDK home, or a folder full of them.
        if (Test-Path -LiteralPath (Join-Path $root 'bin\java.exe')) {
            Write-Output $root
            continue
        }
        foreach ($dir in (Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue)) {
            if (Test-Path -LiteralPath (Join-Path $dir.FullName 'bin\java.exe')) {
                Write-Output $dir.FullName
            }
        }
    }
}

# --------------------------------------------------------------- choose a JDK
$chosen = $null
$chosenVersion = $null
$seen = @()

if ($JdkPath) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\java.exe'))) {
        Write-Host "Not a JDK folder (no bin\java.exe in it): $JdkPath" -ForegroundColor Red
        exit 1
    }
    $chosen = $JdkPath
    $chosenVersion = Get-JdkVersion $JdkPath
    if ($chosenVersion -and $chosenVersion -notlike '17*') {
        Write-Host "Warning: $JdkPath reports Java $chosenVersion, not 17." -ForegroundColor Yellow
        Write-Host "Gradle 8.7 accepts 8-21, so this works, but CI uses 17." -ForegroundColor Yellow
    }
} else {
    foreach ($home in (Get-CandidateHomes)) {
        $version = Get-JdkVersion $home
        if (-not $version) { continue }
        $seen += ("{0,-12} {1}" -f $version, $home)
        if (-not $chosen -and $version -like '17*') {
            $chosen = $home
            $chosenVersion = $version
        }
    }
}

if (-not $chosen) {
    Write-Host ''
    if ($seen.Count -gt 0) {
        Write-Host 'JDKs found, none of them version 17:' -ForegroundColor Yellow
        $seen | ForEach-Object { Write-Host "  $_" }
        Write-Host ''
    } else {
        Write-Host 'No JDK found in the usual places.' -ForegroundColor Yellow
        Write-Host ''
    }
    Write-Host 'Install JDK 17 first, then re-run this script:' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Android Studio (no admin rights needed, installs into %USERPROFILE%\.jdks):'
    Write-Host '    Settings > Build, Execution, Deployment > Build Tools > Gradle'
    Write-Host '    > Gradle JDK > Download JDK... > Version 17 > Download'
    Write-Host ''
    Write-Host '  or from a terminal (needs admin):'
    Write-Host '    winget install EclipseAdoptium.Temurin.17.JDK'
    Write-Host ''
    exit 1
}

Write-Host "Using JDK $chosenVersion" -ForegroundColor Green
Write-Host "  $chosen"

# ------------------------------------------------------- write the Gradle home
$propsDir  = Join-Path $env:USERPROFILE '.gradle'
$propsFile = Join-Path $propsDir 'gradle.properties'
New-Item -ItemType Directory -Force -Path $propsDir | Out-Null

$lines = @()
if (Test-Path -LiteralPath $propsFile) {
    Copy-Item -LiteralPath $propsFile -Destination "$propsFile.bak" -Force
    # Drop any previous setting so re-running does not stack duplicates.
    $lines = @(Get-Content -LiteralPath $propsFile | Where-Object { $_ -notmatch '^\s*org\.gradle\.java\.home\s*=' })
    Write-Host "Previous file backed up to gradle.properties.bak"
}

$normalised = $chosen -replace '\\', '/'
$lines += "org.gradle.java.home=$normalised"
Set-Content -LiteralPath $propsFile -Value $lines -Encoding ASCII

Write-Host ''
Write-Host "Written to $propsFile :" -ForegroundColor Green
Get-Content -LiteralPath $propsFile | ForEach-Object { Write-Host "  $_" }

# ------------------------------------------------------------------ JAVA_HOME
if ($SetJavaHome) {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $chosen, 'User')
    Write-Host ''
    Write-Host "JAVA_HOME set to $chosen for your user account (open a NEW terminal for it to apply)." -ForegroundColor Green
}

Write-Host ''
Write-Host 'Now run:  .\gradlew assembleDebug' -ForegroundColor Green
Write-Host ''
Write-Host 'Android Studio: set the same version under Settings > Build, Execution,'
Write-Host 'Deployment > Build Tools > Gradle > Gradle JDK, then File > Sync Project'
Write-Host 'with Gradle Files. (If both that setting and this property are present,'
Write-Host 'Android Studio warns about the conflict - the property wins. Keep both on 17.)'
