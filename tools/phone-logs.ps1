# Copies Outspoken's run logs and its logcat lines from the phone into phone-logs\ every 30 s,
# so they can be read on the laptop while the phone is in use. Works over USB or wireless
# debugging. phone-logs\ is ignored by git: the logs hold what people said, and the repo is public.
#
#   powershell -ExecutionPolicy Bypass -File tools\phone-logs.ps1          # keep copying
#   powershell -ExecutionPolicy Bypass -File tools\phone-logs.ps1 -Once    # copy once
param([int]$EverySeconds = 30, [switch]$Once)

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
$out = Join-Path (Split-Path $PSScriptRoot -Parent) 'phone-logs'
New-Item -ItemType Directory -Force -Path $out | Out-Null

while ($true) {
    $state = (& $adb get-state 2>$null)
    if ($state -ne 'device') {
        Write-Host "$(Get-Date -Format HH:mm:ss) phone not found: plug it in or connect wireless debugging"
    } else {
        & $adb pull /sdcard/Android/data/com.outspoken/files/logs/. $out 2>&1 | Out-Null
        & $adb logcat -d -s Outspoken | Out-File -Encoding utf8 (Join-Path $out 'logcat-outspoken.txt')
        & $adb logcat -d -b crash | Out-File -Encoding utf8 (Join-Path $out 'logcat-crash.txt')
        $newest = Get-ChildItem $out -Filter *.log | Sort-Object Name | Select-Object -Last 1
        Write-Host "$(Get-Date -Format HH:mm:ss) copied to $out, newest run $($newest.Name)"
    }
    if ($Once) { break }
    Start-Sleep -Seconds $EverySeconds
}
