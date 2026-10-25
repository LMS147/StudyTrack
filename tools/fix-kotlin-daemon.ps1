# StudyTrack — clear out stale Kotlin/Gradle compile daemons
#
# Why: "Could not connect to Kotlin compile daemon" is thrown when Gradle cannot
# reach the separate Kotlin compile daemon. The usual culprit on a machine that
# has built this project before is leftover state — a daemon process that died
# without cleaning up, or a port registry file pointing at a socket that nothing
# is listening on any more. Gradle then tries to reuse the dead registration and
# fails to connect.
#
# This script removes that state. It does NOT touch the repository, your source,
# or your gradle.properties.
#
# Note: gradle.properties now sets kotlin.compiler.execution.strategy=in-process,
# which means no Kotlin daemon is started at all and this script should not be
# needed. It is kept for two cases:
#   - you switched back to the daemon strategy for speed, or
#   - you are on an older checkout that still uses the daemon.
#
# Run it normally (do NOT dot-source it):
#   .\tools\fix-kotlin-daemon.ps1
#
# If Windows blocks scripts:
#   powershell -ExecutionPolicy Bypass -File .\tools\fix-kotlin-daemon.ps1
#
# Just show what it would remove:
#   .\tools\fix-kotlin-daemon.ps1 -WhatIf

[CmdletBinding(SupportsShouldProcess = $true)]
param()

$ErrorActionPreference = 'Continue'

Write-Host "StudyTrack - clearing stale compile daemon state" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------------------------
# 1. Ask Gradle to shut its daemons down cleanly first, so we are not deleting
#    the state of a daemon that is about to write it back.
# ---------------------------------------------------------------------------
$gradlew = Join-Path $PSScriptRoot '..\gradlew.bat'
if (Test-Path $gradlew) {
    Write-Host "Stopping Gradle daemons..."
    & $gradlew --stop 2>&1 | Out-Null
} else {
    Write-Host "gradlew.bat not found next to this script - skipping --stop" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2. Kill any surviving Kotlin compile daemon JVMs. These are identifiable by
#    the KotlinDaemonMain entry point on their command line, which keeps us from
#    killing unrelated Java processes (Android Studio, for one).
# ---------------------------------------------------------------------------
$killed = 0
try {
    $procs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop
} catch {
    $procs = @()
    Write-Host "Could not enumerate java.exe processes: $($_.Exception.Message)" -ForegroundColor Yellow
}

foreach ($p in $procs) {
    if ($p.CommandLine -and $p.CommandLine -match 'KotlinCompileDaemon|KotlinDaemonMain') {
        if ($PSCmdlet.ShouldProcess("PID $($p.ProcessId)", "Stop Kotlin compile daemon")) {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            $killed++
        }
    }
}
Write-Host "Stopped $killed Kotlin compile daemon process(es)."

# ---------------------------------------------------------------------------
# 3. Remove the Kotlin daemon's own state directory. This holds the port
#    registry that Gradle reads to find a running daemon - the thing that goes
#    stale and causes the connection failure.
# ---------------------------------------------------------------------------
$kotlinDaemonDir = Join-Path $env:USERPROFILE '.kotlin\daemon'
if (Test-Path $kotlinDaemonDir) {
    if ($PSCmdlet.ShouldProcess($kotlinDaemonDir, "Remove Kotlin daemon state")) {
        Remove-Item -Recurse -Force $kotlinDaemonDir -ErrorAction SilentlyContinue
        Write-Host "Removed $kotlinDaemonDir"
    }
} else {
    Write-Host "No Kotlin daemon state directory at $kotlinDaemonDir"
}

# ---------------------------------------------------------------------------
# 4. Remove the per-JDK daemon log/lock directories the Kotlin plugin keeps in
#    the temp folder. Safe to delete; they are recreated on the next build.
# ---------------------------------------------------------------------------
$tempRoot = $env:TEMP
if ($tempRoot) {
    $patterns = @('kotlin-daemon.*', 'kotlin-idea-*')
    foreach ($pat in $patterns) {
        Get-ChildItem -Path $tempRoot -Filter $pat -ErrorAction SilentlyContinue | ForEach-Object {
            if ($PSCmdlet.ShouldProcess($_.FullName, "Remove stale daemon artifact")) {
                Remove-Item -Recurse -Force $_.FullName -ErrorAction SilentlyContinue
                Write-Host "Removed $($_.FullName)"
            }
        }
    }
}

# ---------------------------------------------------------------------------
# 5. Report which strategy is in effect, so it is obvious whether a daemon is
#    even supposed to exist.
# ---------------------------------------------------------------------------
Write-Host ""
$repoProps = Join-Path $PSScriptRoot '..\gradle.properties'
$strategy = '(not set - Kotlin defaults to "daemon")'
if (Test-Path $repoProps) {
    $line = Select-String -Path $repoProps -Pattern '^\s*kotlin\.compiler\.execution\.strategy\s*=' | Select-Object -First 1
    if ($line) { $strategy = $line.Line.Trim() }
}
Write-Host "Effective kotlin.compiler.execution.strategy in the repo:" -ForegroundColor Cyan
Write-Host "  $strategy"
Write-Host ""
Write-Host "Done. Rebuild with: .\gradlew assembleDebug" -ForegroundColor Green
Write-Host "If it still cannot connect, the daemon is being blocked at the socket level."
Write-Host "Switch strategy instead of fighting it - add to ~/.gradle/gradle.properties:"
Write-Host "  kotlin.compiler.execution.strategy=in-process"
