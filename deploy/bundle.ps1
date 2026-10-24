# Windows PowerShell 5.1+. The release needs Docker Desktop only, no Git/build tools.
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop')][string]$Action = 'start',
    [ValidateRange(1024, 65535)][int]$Port = 18000,
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName = 'wealthflow',
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $bundleRoot 'compose.yaml'
$composeArgs = @('compose', '--project-name', $ProjectName, '--project-directory', $bundleRoot, '-f', $composeFile)
$oldPort = $env:WEALTHFLOW_HTTP_PORT
$env:WEALTHFLOW_HTTP_PORT = "$Port"

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker failed (exit $LASTEXITCODE). See the output above." }
}
function Has-ReleaseImages($Manifest) {
    foreach ($entry in $Manifest.images) {
        $id = & docker image ls --no-trunc --quiet $entry.tag
        if ($LASTEXITCODE -ne 0 -or "$id".Trim() -ne $entry.id) { return $false }
    }
    return $true
}

try {
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install Docker Desktop, start it, then double-click start.cmd again.' }
    $engine = & docker info --format '{{.OSType}}/{{.Architecture}}' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not ready. Start Docker Desktop and wait for the engine, then try again.' }
    if ("$engine" -notlike 'linux/*') { throw 'Switch Docker Desktop to Linux containers, then try again.' }
    Invoke-Docker @('compose', 'version')
    if ($Action -eq 'stop') {
        Invoke-Docker ($composeArgs + @('stop'))
        Write-Host 'WealthFlow stopped. Your data and master key are preserved.'
        return
    }
    $manifest = Get-Content -LiteralPath (Join-Path $bundleRoot 'release.json') -Raw | ConvertFrom-Json
    $engineArch = ("$engine".Trim() -split '/')[1]
    if ($engineArch -eq 'x86_64') { $engineArch = 'amd64' }
    if ($engineArch -eq 'aarch64') { $engineArch = 'arm64' }
    foreach ($entry in $manifest.images) {
        if ($entry.architecture -ne $engineArch) { throw "This package is for $($entry.architecture); Docker uses $engineArch. Request the matching package." }
    }
    if (!(Has-ReleaseImages $manifest)) {
        $archive = Join-Path $bundleRoot 'images.tar'
        if (!(Test-Path -LiteralPath $archive)) { throw 'images.tar is missing. Fully extract the release ZIP before starting.' }
        Write-Host 'Checking and importing packaged images (first start can take a few minutes)...'
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $manifest.archiveSha256) { throw 'images.tar is damaged. Copy or download the release ZIP again.' }
        Invoke-Docker @('image', 'load', '--input', $archive)
        if (!(Has-ReleaseImages $manifest)) { throw 'Imported images do not match this release.' }
    }
    & (Join-Path $PSScriptRoot 'init-secrets.ps1')
    Invoke-Docker ($composeArgs + @('up', '-d', '--pull', 'never', '--no-build', '--wait', '--wait-timeout', '180'))
    $url = "http://127.0.0.1:$Port"
    $health = Invoke-RestMethod -Uri "$url/api/v1/system/health" -TimeoutSec 15
    if ($health.status -ne 'UP') { throw 'The backend health check failed.' }
    Write-Host "WealthFlow is ready: $url"
    if (!$NoBrowser) {
        try { Start-Process $url } catch { Write-Host "Open $url in your browser." }
    }
} catch {
    Write-Host "`nUnable to $Action WealthFlow: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Diagnostics: docker compose --project-name $ProjectName -f `"$composeFile`" logs --tail 100"
    exit 1
} finally { $env:WEALTHFLOW_HTTP_PORT = $oldPort }
