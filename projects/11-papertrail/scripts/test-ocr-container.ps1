param(
    [string]$ImageTag = "papertrail-ocr:smoke"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path (Join-Path $projectRoot '../..')).Path
$fixture = Join-Path $projectRoot 'src/test/resources/fixtures/invoice-ocr-fixture.pdf'
$containerName = "papertrail-ocr-smoke-$([guid]::NewGuid().ToString('N').Substring(0, 12))"

if (-not (Test-Path -LiteralPath $fixture)) {
    throw "OCR fixture not found: $fixture"
}

Push-Location $repoRoot
try {
    docker build --tag $ImageTag projects/11-papertrail
    if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed.' }

    docker run --detach --rm --name $containerName --publish '127.0.0.1::8091' $ImageTag
    if ($LASTEXITCODE -ne 0) { throw 'Docker container failed to start.' }

    $mapping = $null
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        $mapping = docker port $containerName '8091/tcp' 2>$null
        if ($LASTEXITCODE -eq 0 -and $mapping -match '127\.0\.0\.1:(\d+)') { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($mapping -notmatch '127\.0\.0\.1:(\d+)') { throw 'Could not resolve the container host port.' }
    $baseUrl = "http://127.0.0.1:$($Matches[1])"

    $healthy = $false
    do {
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { Start-Sleep -Milliseconds 750 }
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $healthy) { throw 'PaperTrail container did not become healthy within 90 seconds.' }

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $stream = [System.IO.File]::OpenRead($fixture)
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/pdf')
    $form.Add($fileContent, 'file', 'invoice-ocr-fixture.pdf')
    try {
        $response = $client.PostAsync("$baseUrl/api/invoices/extract-file", $form).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) { throw "OCR upload failed ($([int]$response.StatusCode)): $body" }
        $result = $body | ConvertFrom-Json
        if ($result.invoiceNumber -ne 'INV-PT-2026-001') { throw "OCR did not recover the fixture invoice number: $($result.invoiceNumber)" }
        if ($result.supplier -ne 'Northstar Supplies') { throw "OCR did not recover the fixture supplier: $($result.supplier)" }
        if ($result.total -ne 21.60 -or $result.tax -ne 1.60 -or $result.lineItemsTotal -ne 20.00) {
            throw "OCR arithmetic fields did not match fixture expectations. total=$($result.total), tax=$($result.tax), subtotal=$($result.lineItemsTotal)"
        }
        if ($result.status -eq 'APPROVED' -or $result.status -notin @('MANUAL_REVIEW_REQUIRED', 'EXTRACTED_REVIEW_BEFORE_PAYMENT')) {
            throw "Unexpected extraction status: $($result.status)"
        }
        if ($result.notice -notmatch 'verify every field against the original') {
            throw 'OCR response omitted the required human-verification notice.'
        }
        if ($result.gstin -and $result.fieldConfidence.gstin -ne 'FORMAT_AND_CHECKSUM') {
            throw 'Returned GSTIN is not marked as checksum-validated.'
        }
        [pscustomobject]@{
            status = $result.status
            invoiceNumber = $result.invoiceNumber
            supplier = $result.supplier
            gstin = $result.gstin
            subtotal = $result.lineItemsTotal
            tax = $result.tax
            total = $result.total
            humanVerificationRequired = $true
        } | ConvertTo-Json -Depth 4
    } finally {
        $form.Dispose()
        $client.Dispose()
        $stream.Dispose()
    }
} finally {
    docker stop $containerName 2>$null | Out-Null
    Pop-Location
}
