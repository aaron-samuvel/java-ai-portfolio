$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $PSScriptRoot '.run/portfolio-processes.json'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    Write-Host 'No portfolio process manifest was found.'
    exit 0
}

$entries = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
foreach ($entry in $entries) {
    try { $process = Get-Process -Id ([int]$entry.pid) -ErrorAction Stop }
    catch { Write-Host "Already stopped: $($entry.name) (PID $($entry.pid))"; continue }

    $expectedStart = [DateTimeOffset]::Parse($entry.startTime).UtcDateTime
    $actualStart = $process.StartTime.ToUniversalTime()
    if ([Math]::Abs(($actualStart - $expectedStart).TotalSeconds) -gt 5) {
        Write-Warning "Skipping PID $($entry.pid): it no longer matches the recorded $($entry.name) process start time."
        continue
    }

    & taskkill.exe /PID ([int]$entry.pid) /T /F | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "Stopped $($entry.name) process tree (PID $($entry.pid))." }
    else { Write-Warning "Could not stop $($entry.name) process tree (PID $($entry.pid))." }
}

Remove-Item -LiteralPath $manifestPath -Force
