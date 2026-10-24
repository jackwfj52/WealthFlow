# Uses a unique Compose project and synthetic data, then removes only that project's test volume.
[CmdletBinding()]
param([int]$Port = 18001)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$projectName = 'wealthflow-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$oldPort = $env:WEALTHFLOW_HTTP_PORT
$base = "http://127.0.0.1:$Port"
$checks = [System.Collections.Generic.List[string]]::new()
function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed (exit $LASTEXITCODE)." }
}
function Api([string]$Method, [string]$Path, $Body = $null) {
    $options = @{ Uri = "$base/api/v1$Path"; Method = $Method; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $options.ContentType = 'application/json; charset=utf-8'
        $options.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 100 -Compress))
    }
    $reply = Invoke-RestMethod @options
    if ($reply.code -ne 0) { throw "API operation failed at $Path" }
    return $reply.data
}
function Check([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "Check failed: $Name" }
    $checks.Add($Name)
    Write-Host "PASS: $Name"
}
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid test port.' }
& ./deploy/init-secrets.ps1
$env:WEALTHFLOW_HTTP_PORT = "$Port"
try {
    Invoke-Docker @('compose', '-p', $projectName, 'up', '-d', '--no-build', '--wait', '--wait-timeout', '120')
    Check ((Invoke-RestMethod "$base/api/v1/system/health").status -eq 'UP') 'backend health through frontend proxy'
    $backendId = (& docker compose -p $projectName ps -q backend).Trim()
    $backend = (& docker inspect $backendId | ConvertFrom-Json)[0]
    Check ($backend.Config.Labels.'com.docker.compose.project' -eq $projectName) 'isolated test project identity'
    Check ($backend.Config.User -eq 'wealthflow' -and $backend.HostConfig.ReadonlyRootfs) 'non-root backend and read-only filesystem'
    Check (@(Api 'GET' '/categories').Count -eq 0) 'fresh isolated database'
    $category = Api 'POST' '/categories' @{ name = 'Docker check'; color = '#13c2c2' }
    $null = Api 'POST' '/snapshots' @{ snapshotDate = '2026-01-01'; items = @(@{ categoryId = $category.id; amount = 1234.56 }) }
    $null = Api 'PUT' "/holdings/$($category.id)" @{ items = @(@{ name = 'Fixture holding'; percentage = 60; sector = 'TECH' }) }
    $provider = @{ providerId = 'docker-fixture'; displayName = 'Docker fixture'; protocol = 'OPENAI_COMPATIBLE'; baseUrl = 'https://example.invalid/v1'; model = 'fixture'; apiKey = 'docker-fixture-key-123456' }
    $saved = Api 'POST' '/agent/providers' $provider
    Check ($saved.configured -and !$saved.PSObject.Properties['apiKey']) 'AES provider save and masked response'
    $null = Api 'PUT' '/knowledge/embedding' @{ mode = 'CUSTOM'; baseUrl = 'https://example.invalid/v1'; model = 'fixture-embedding'; apiKey = 'embedding-fixture-key-123456'; enabled = $false }
    $document = Api 'POST' '/knowledge/documents' @{ title = 'Docker persistence'; type = 'GENERAL_NOTE'; format = 'TEXT'; body = 'docker persistence smoke test'; tags = @('docker'); status = 'ACTIVE' }
    $conversation = Api 'POST' '/agent/conversations' @{}
    $null = Api 'POST' "/agent/conversations/$($conversation.id)/import" @{ messages = @(@{ role = 'user'; content = 'docker fixture question' }, @{ role = 'assistant'; content = 'docker fixture answer' }) }
    $page = Invoke-WebRequest "$base/agent" -UseBasicParsing
    Check ($page.StatusCode -eq 200 -and $page.Content.Contains('id="root"')) 'SPA direct route fallback'
    Invoke-Docker @('compose', '-p', $projectName, 'exec', '-T', 'backend', 'python', '-c', 'import akshare; print(akshare.__version__)')
    $backup = Api 'GET' '/system/full-backup'
    Check (!(($backup | ConvertTo-Json -Depth 100).Contains('docker-fixture-key-123456'))) 'backup excludes configured API key'
    Invoke-Docker @('compose', '-p', $projectName, 'up', '-d', '--no-build', '--force-recreate', '--wait', '--wait-timeout', '120')
    Check (@(Api 'GET' '/snapshots').Count -eq 1 -and @(Api 'GET' '/holdings').Count -eq 1) 'snapshot and holdings survive container recreation'
    Check (@(Api 'GET' '/agent/providers')[0].configured) 'provider key decrypts after recreation'
    Check ((Api 'GET' '/knowledge/embedding').configured) 'embedding key decrypts after recreation'
    Check ((Api 'GET' "/agent/conversations/$($conversation.id)").messages.Count -eq 2) 'conversation survives recreation'
    Check ((Api 'POST' '/knowledge/search' @{ query = 'persistence'; limit = 3 }).results.Count -ge 1) 'knowledge remains searchable'
    $null = Api 'POST' '/system/full-restore' $backup
    Check (!@(Api 'GET' '/agent/providers')[0].configured) 'portable restore clears model keys'
    $saved = Api 'PUT' '/agent/providers/docker-fixture' $provider
    Check ($saved.configured) 'restored provider accepts a replacement key'
    $reportPath = Join-Path '.wealthflow' ('docker-check-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
    @{ project = $projectName; syntheticData = $true; externalModelCalls = 0; checks = @($checks); timestamp = (Get-Date).ToString('o') } |
        ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Docker smoke test passed. Report: $reportPath"
} catch {
    & docker compose -p $projectName logs --tail 50
    throw
} finally {
    # The generated project name ensures no existing application or user volume is targeted.
    & docker compose -p $projectName down --volumes
    $env:WEALTHFLOW_HTTP_PORT = $oldPort
}
