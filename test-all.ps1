$ErrorActionPreference = 'Continue'
$PSNativeCommandUseErrorActionPreference = $false
$failed = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'projects') -Directory | Sort-Object Name | ForEach-Object {
    $name = $_.Name
    Write-Host "Testing $name ..."
    Push-Location $_.FullName
    try {
        & mvn -B -q test
        if ($LASTEXITCODE -ne 0) { $failed.Add($name) }
        else { Write-Host "PASS $name" }
    }
    finally { Pop-Location }
}

$frontend = Join-Path $PSScriptRoot 'projects\01-documind\frontend'
Push-Location $frontend
try {
    foreach ($script in @('test', 'build')) {
        Write-Host "Running DocuMind frontend npm $script ..."
        & npm run $script
        if ($LASTEXITCODE -ne 0) { $failed.Add("documind-frontend-$script") }
        else { Write-Host "PASS documind-frontend-$script" }
    }
}
finally { Pop-Location }

if ($failed.Count -gt 0) {
    Write-Error "Failed projects: $($failed -join ', ')"
    exit 1
}
Write-Host 'All twelve Java project suites and the DocuMind frontend test/build passed.'
