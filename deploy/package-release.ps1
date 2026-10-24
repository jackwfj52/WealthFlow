# Creates a source-free, offline Windows distribution. Run on the maintainer's PC.
[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot '.wealthflow/releases' }
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker failed (exit $LASTEXITCODE): $($Arguments -join ' ')" }
}
function Write-Utf8([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install and start Docker Desktop first.' }
Invoke-Docker @('info', '--format', '{{.OSType}}/{{.Architecture}}')
Invoke-Docker @('compose', 'version')
Push-Location -LiteralPath $projectRoot
try {
    if (!$SkipBuild) { Invoke-Docker @('compose', '-f', 'compose.yaml', 'build') }
    # Render once, then remove build contexts and all host-specific paths/names.
    $rendered = & docker compose -f compose.yaml config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot render Compose configuration.' }
    $config = ($rendered -join "`n") | ConvertFrom-Json
    $release = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
    $images = @()
    foreach ($service in @('backend', 'frontend')) {
        $definition = $config.services.$service
        $metadata = & docker image inspect $definition.image
        if ($LASTEXITCODE -ne 0) { throw "Missing $service image. Run without -SkipBuild." }
        $metadata = @(($metadata -join "`n") | ConvertFrom-Json)[0]
        if ($metadata.Os -ne 'linux') { throw 'Only Linux container images are supported.' }
        $tag = "wealthflow-${service}:$release"
        Invoke-Docker @('image', 'tag', $definition.image, $tag)
        $definition.image = $tag
        $definition.PSObject.Properties.Remove('build')
        $definition | Add-Member -NotePropertyName pull_policy -NotePropertyValue never -Force
        $images += [pscustomobject]@{ tag = $tag; id = $metadata.Id; os = $metadata.Os; architecture = $metadata.Architecture }
    }
    if ($images[0].architecture -ne $images[1].architecture) { throw 'Frontend and backend image architectures differ.' }
    $config.secrets.master_key.file = './.wealthflow/docker/master-key.txt'
    $config.secrets.master_key.PSObject.Properties.Remove('name')
    $config.networks.default.PSObject.Properties.Remove('name')
    $config.volumes.wealthflow_data.PSObject.Properties.Remove('name')
    $config.services.frontend.ports[0].published = '${WEALTHFLOW_HTTP_PORT:-18000}'
    $architecture = $images[0].architecture
    $bundleName = "WealthFlow-windows-$architecture-$release"
    $bundle = Join-Path $OutputDirectory $bundleName
    if (Test-Path -LiteralPath $bundle) { throw "Output already exists: $bundle" }
    New-Item -ItemType Directory -Path (Join-Path $bundle 'deploy') -Force | Out-Null
    Write-Utf8 (Join-Path $bundle 'compose.yaml') ($config | ConvertTo-Json -Depth 100)
    foreach ($file in @('bundle.ps1', 'init-secrets.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination (Join-Path $bundle 'deploy')
    }
    foreach ($action in @('start', 'stop')) {
        $command = "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0deploy\bundle.ps1`" -Action $action`r`nif errorlevel 1 (`r`n  pause`r`n  exit /b 1`r`n)`r`n"
        Write-Utf8 (Join-Path $bundle "$action.cmd") $command
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'BUNDLE-README.md') -Destination (Join-Path $bundle 'README.md')
    $archive = Join-Path $bundle 'images.tar'
    Invoke-Docker (@('image', 'save', '--output', $archive) + @($images | ForEach-Object { $_.tag }))
    $manifest = [ordered]@{
        release = $release
        images = $images
        archiveSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    }
    Write-Utf8 (Join-Path $bundle 'release.json') ($manifest | ConvertTo-Json -Depth 10)
    # ZipArchive streaming supports large images.tar files; Compress-Archive has a 2 GB file limit.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = Join-Path $OutputDirectory "$bundleName.zip"
    [System.IO.Compression.ZipFile]::CreateFromDirectory($bundle, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
    Write-Host "Release ready: $zip"
    Write-Host 'Send only this ZIP. It contains no personal database, API keys, or source checkout.'
} finally { Pop-Location }
