# Compatible with Windows PowerShell 5.1+. Never replaces an existing master key.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $projectRoot '.wealthflow/docker'
$keyPath = Join-Path $keyDirectory 'master-key.txt'
New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
if (!(Test-Path -LiteralPath $keyPath)) {
    $bytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $encoded = [System.Text.Encoding]::ASCII.GetBytes([Convert]::ToBase64String($bytes))
    $file = [System.IO.File]::Open($keyPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try { $file.Write($encoded, 0, $encoded.Length) } finally { $file.Dispose() }
    [Array]::Clear($bytes, 0, $bytes.Length)
}
try {
    if ((Get-Item -LiteralPath $keyPath).Length -gt 256) { throw 'invalid key size' }
    $decoded = [Convert]::FromBase64String([System.IO.File]::ReadAllText($keyPath).Trim())
    if ($decoded.Length -ne 32) { throw 'invalid key length' }
    [Array]::Clear($decoded, 0, $decoded.Length)
} catch { throw 'Existing master key is invalid. Restore the original key; do not overwrite it for an existing database.' }
Write-Host 'Docker master key is ready in .wealthflow/docker/master-key.txt (never commit or replace it).'
