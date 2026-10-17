param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('01-documind','02-helpdesk-copilot','03-toolbridge','04-research-crew','05-reviewpilot','06-fraudguard','07-visioncheck','08-askyourdata','09-voicenotes','10-shelfwise','11-papertrail','12-evalops')]
    [string]$Project
)

$projectPath = Join-Path $PSScriptRoot "projects/$Project"
$pomPath = Join-Path $projectPath 'pom.xml'
if (-not (Test-Path -LiteralPath $pomPath)) {
    throw "Project has no Maven application yet: $Project"
}

Push-Location $projectPath
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
