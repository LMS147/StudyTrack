# StudyTrack — point Gradle at a JDK it can run on
#
# Why: Gradle versions below 9.0.0 cannot run on a JVM 25 or newer (the Kotlin
# version they embed cannot read it — fixed in Kotlin 2.1.20). The build stops
# with an error whose entire message is the version number:
#
#   * What went wrong:
#   25.0.1
#
# Gradle 8.7 can run on Java 8-21, so JDK 17 (what CI uses) or the JDK bundled
# with Android Studio (17 or 21, depending on its version) both work. This
# script prefers 17 so the machine matches CI, but it will happily use any
# other supported JDK rather than sending you off to install one.
#
# It writes org.gradle.java.home into the machine-local
# ~/.gradle/gradle.properties, which is where Gradle looks for it. Nothing in
# the repository is touched.
#
# Run it normally (do NOT dot-source it):
#   .\tools\set-gradle-jdk17.ps1
#
# If Windows blocks scripts: powershell -ExecutionPolicy Bypass -File .\tools\set-gradle-jdk17.ps1
# Choose a specific JDK:      .\tools\set-gradle-jdk17.ps1 -JdkPath "C:\Users\me\.jdks\temurin-17.0.11"
# Also set JAVA_HOME:         .\tools\set-gradle-jdk17.ps1 -SetJavaHome
# Just show what it found:    .\tools\set-gradle-jdk17.ps1 -WhatIf

[CmdletBinding()]
param(
    [string]$JdkPath,
    [switch]$SetJavaHome,
    [switch]$WhatIf
)

# NOTE: deliberately NOT 'Stop'. Probing a JDK writes its version to stderr, and
# on Windows PowerShell 5.1 that becomes a NativeCommandError which would abort
# this script mid-scan (it did, once). Errors are checked explicitly instead.
$ErrorActionPreference = 'Continue'

# Versions that can run this project's Gradle (8.7 supports 8-21).
$minMajor = 8
$maxMajor = 21

function Get-JdkVersion {
    param([string]$Home)

    # Prefer the `release` file inside the JDK: plain text, no process, no
    # stderr, no PowerShell error-stream weirdness.
    $release = Join-Path $Home 'release'
    if (Test-Path -LiteralPath $release) {
        $raw = Get-Content -LiteralPath $release -Raw -ErrorAction SilentlyContinue
        if ($raw -match 'JAVA_VERSION\s*=\s*"([^"]+)"') { return $Matches[1] }
    }

    # Fallback: run java through cmd so cmd does the redirecting, not PowerShell.
    $exe = Join-Path $Home 'bin\java.exe'
    if (Test-Path -LiteralPath $exe) {
        $out = cmd /c "`"$exe`" -version 2>&1"
        if ("$out" -match 'version "([^"]+)"') { return $Matches[1] }
    }
    return $null
}

function Get-MajorVersion {
    param([string]$Version)
    if (-not $Version) { return $null }
    if ($Version -like '1.*') { return 8 }          # 1.8.0_392 is Java 8
    $first = ($Version -split '[.\-_]')[0]
    if ($first -match '^\d+$') { return [int]$first }
    return $null
}

function Get-CandidateHomes {
    $roots = @(
        (Join-Path $env:USERPROFILE '.jdks'),                          # Android Studio's "Download JDK"
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'),   # Android Studio (Toolbox install)
        'C:\Program Files\Android\Android Studio\jbr',                 # Android Studio (standard install)
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu',
        'C:\Program Files\BellSoft'
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        # A path is either a JDK home itself, or a folder containing several.
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

# ---------------------------------------------------------------- survey JDKs
$candidates = @()   # @{ Home; Version; Major }
$seen = @()

$homes = if ($JdkPath) { @($JdkPath) } else { @(Get-CandidateHomes) }
foreach ($home in $homes) {
    $version = Get-JdkVersion $home
    if (-not $version) { continue }
    $major = Get-MajorVersion $version
    $candidates += [pscustomobject]@{ Home = $home; Version = $version; Major = $major }
    $seen += ("{0,-14} {1}" -f $version, $home)
}

if ($seen.Count -gt 0) {
    Write-Host 'JDKs found on this machine:' -ForegroundColor Cyan
    $seen | ForEach-Object { Write-Host "  $_" }
    Write-Host ''
}

# Preference: exactly 17 (matches CI), then the newest version Gradle supports.
$usable = @($candidates | Where-Object { $_.Major -ne $null -and $_.Major -ge $minMajor -and $_.Major -le $maxMajor })
$chosen = $null
if ($usable.Count -gt 0) {
    $exact = @($usable | Where-Object { $_.Major -eq 17 })
    $chosen = if ($exact.Count -gt 0) { $exact[0] } else { ($usable | Sort-Object Major -Descending)[0] }
}

if (-not $chosen) {
    Write-Host "No usable JDK (Java $minMajor-$maxMajor) found." -ForegroundColor Yellow
    if ($candidates.Count -gt 0) {
        Write-Host 'Everything found was outside that range - most likely Java 25, which Gradle 8.7 cannot run on.' -ForegroundColor Yellow
    }
    Write-Host ''
    Write-Host 'Android Studio already ships a usable JDK. Point this script at it:' -ForegroundColor Yellow
    Write-Host '  .\tools\set-gradle-jdk17.ps1 -JdkPath "C:\Program Files\Android\Android Studio\jbr"'
    Write-Host '  (Toolbox installs live in %LOCALAPPDATA%\Programs\Android Studio\jbr)'
    Write-Host ''
    Write-Host 'Or install one: Android Studio > Gradle settings > Gradle JDK > Download JDK...,'
    Write-Host 'or from a terminal:  winget install EclipseAdoptium.Temurin.17.JDK'
    Write-Host ''
    exit 1
}

Write-Host "Using JDK $($chosen.Version)" -ForegroundColor Green
Write-Host "  $($chosen.Home)"
if ($chosen.Major -ne 17) {
    Write-Host "  (Java $($chosen.Major) runs Gradle 8.7 fine - Java $minMajor-$maxMajor all work. CI uses 17.)" -ForegroundColor DarkGray
}
Write-Host ''

if ($WhatIf) {
    Write-Host 'WhatIf: nothing was written. Re-run without -WhatIf to apply.' -ForegroundColor Yellow
    exit 0
}

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

$normalised = $chosen.Home -replace '\\', '/'
$lines += "org.gradle.java.home=$normalised"
Set-Content -LiteralPath $propsFile -Value $lines -Encoding ASCII

Write-Host ''
Write-Host "Written to $propsFile :" -ForegroundColor Green
Get-Content -LiteralPath $propsFile | ForEach-Object { Write-Host "  $_" }

# ------------------------------------------------------------------ JAVA_HOME
if ($SetJavaHome) {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $chosen.Home, 'User')
    Write-Host ''
    Write-Host "JAVA_HOME set to $($chosen.Home) for your user account (open a NEW terminal for it to apply)." -ForegroundColor Green
}

Write-Host ''
Write-Host 'Now run:  .\gradlew assembleDebug' -ForegroundColor Green
Write-Host ''
Write-Host 'Android Studio: set the same version under Settings > Build, Execution,'
Write-Host 'Deployment > Build Tools > Gradle > Gradle JDK, then File > Sync Project'
Write-Host 'with Gradle Files. (If both that setting and this property are present,'
Write-Host 'Android Studio warns about the conflict - the property wins.)'
