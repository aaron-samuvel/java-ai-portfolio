param(
    [switch]$SkipHealthWait,
    [int]$HealthTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$runtimeDir = Join-Path $root '.run'
$manifestPath = Join-Path $runtimeDir 'portfolio-processes.json'
$projects = @(
    @{ Name = '01-documind'; Port = 8081 },
    @{ Name = '02-helpdesk-copilot'; Port = 8082 },
    @{ Name = '03-toolbridge'; Port = 8083 },
    @{ Name = '04-research-crew'; Port = 8084 },
    @{ Name = '05-reviewpilot'; Port = 8085 },
    @{ Name = '06-fraudguard'; Port = 8086 },
    @{ Name = '07-visioncheck'; Port = 8087 },
    @{ Name = '08-askyourdata'; Port = 8088 },
    @{ Name = '09-voicenotes'; Port = 8089 },
    @{ Name = '10-shelfwise'; Port = 8090 },
    @{ Name = '11-papertrail'; Port = 8091 },
    @{ Name = '12-evalops'; Port = 8092 }
)

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) { $maven = Get-Command mvn -ErrorAction SilentlyContinue }
if (-not $maven) { throw 'Maven was not found on PATH.' }
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

$started = @()
foreach ($project in $projects) {
    $url = "http://127.0.0.1:$($project.Port)/actuator/health"
    try {
        $existing = Invoke-RestMethod -Uri $url -TimeoutSec 2
        if ($existing.status -eq 'UP') {
            Write-Host "Already healthy: $($project.Name) on $($project.Port)"
            continue
        }
    } catch { }

    $directory = Join-Path $root "projects/$($project.Name)"
    $stdout = Join-Path $runtimeDir "$($project.Name).out.log"
    $stderr = Join-Path $runtimeDir "$($project.Name).err.log"
    $process = Start-Process -FilePath $maven.Source -ArgumentList @('-q', 'spring-boot:run') `
        -WorkingDirectory $directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $started += [pscustomobject]@{ name = $project.Name; port = $project.Port; pid = $process.Id; startTime = $process.StartTime.ToUniversalTime().ToString('o') }
    Write-Host "Started $($project.Name) (PID $($process.Id)); logs: $stdout and $stderr"
}

$started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8
if (-not $SkipHealthWait) {
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    foreach ($project in $projects) {
        $url = "http://127.0.0.1:$($project.Port)/actuator/health"
        $healthy = $false
        while ((Get-Date) -lt $deadline) {
            try {
                if ((Invoke-RestMethod -Uri $url -TimeoutSec 2).status -eq 'UP') { $healthy = $true; break }
            } catch { Start-Sleep -Seconds 2 }
        }
        if ($healthy) { Write-Host "HEALTHY $($project.Name)" }
        else { Write-Warning "Not healthy yet: $($project.Name). Check its log under .run/. DocuMind needs PostgreSQL/pgvector and Ollama." }
    }
}
Write-Host "Started processes are recorded in $manifestPath. Use ./stop-portfolio.ps1 to stop only those recorded process trees."
