# Windows PowerShell 5.1+ / PowerShell 7. All child processes belong to this invocation.
[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$Install,
    [switch]$SmokeTest,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [string]$Python,
    [string]$DatabasePath
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
function Run-Checked([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$File failed (exit $LASTEXITCODE)" }
}
function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (!$command) { throw "Missing $Name. See README.md for prerequisites." }
    return $command.Source
}
function Assert-PortFree([int]$Port) {
    if ($Port -lt 1024 -or $Port -gt 65535) { throw "Port must be between 1024 and 65535." }
    $probe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $probe.Server.ExclusiveAddressUse = $true
    try { $probe.Start() }
    catch { throw "Port $Port is occupied or unavailable. Close that application or select another port. No process was stopped." }
    finally { $probe.Stop() }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { Require-Command 'java' }
if (!(Test-Path -LiteralPath $java)) { throw 'JAVA_HOME does not contain bin/java.exe.' }
$javaVersionText = (& $java --version | Out-String)
if ($javaVersionText -notmatch '(?:openjdk|java)\s+(\d+)') { throw 'Cannot detect Java version.' }
if ([int]$Matches[1] -lt 17) { throw 'Java 17+ is required.' }
$node = Require-Command 'node'
$npm = Require-Command 'npm.cmd'
$nodeVersion = [version]((& $node --version).Trim().TrimStart('v'))
if ($nodeVersion -lt [version]'22.12.0') { throw 'Node.js 22.12+ is required by this launcher.' }
$maven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
$mavenPath = if ($maven) { $maven.Source } else { Join-Path $projectRoot 'mvnw.cmd' }
if (!(Test-Path -LiteralPath $mavenPath)) { throw 'Maven or Maven Wrapper is required.' }
if ($Install) {
    Run-Checked $npm @('ci', '--prefix', 'frontend')
    $venvPython = Join-Path $projectRoot '.venv-finance/Scripts/python.exe'
    if (!(Test-Path -LiteralPath $venvPython)) {
        if ($Python) { Run-Checked $Python @('-m', 'venv', '.venv-finance') }
        elseif (Get-Command 'py' -ErrorAction SilentlyContinue) { Run-Checked 'py' @('-3.12', '-m', 'venv', '.venv-finance') }
        else { Run-Checked (Require-Command 'python') @('-m', 'venv', '.venv-finance') }
    }
    Run-Checked $venvPython @('-m', 'pip', 'install', '-r', 'tools/finance/requirements.txt')
}
$financePython = if ($env:WEALTHFLOW_NEWS_PYTHON) { $env:WEALTHFLOW_NEWS_PYTHON } else { Join-Path $projectRoot '.venv-finance/Scripts/python.exe' }
if (Test-Path -LiteralPath $financePython) {
    Run-Checked $financePython @('-c', "import sys, akshare; assert sys.version_info >= (3, 11); print('Finance Python: ' + sys.version.split()[0])")
} else { throw 'Finance Python is missing. Run ./start.ps1 -Install -Python <python.exe> first.' }
if (!(Test-Path -LiteralPath 'frontend/node_modules/vite/bin/vite.js')) { throw 'Frontend dependencies missing. Run ./start.ps1 -Install first.' }
Assert-PortFree $BackendPort
Assert-PortFree $FrontendPort
if ($BackendPort -eq $FrontendPort) { throw 'Backend and frontend need different ports.' }
Write-Host "Environment OK. Java 17+, Node $nodeVersion, finance Python and dependencies ready."
if ($CheckOnly) { exit 0 }
Run-Checked $mavenPath @('-DskipTests', 'package')
Run-Checked $npm @('run', 'build', '--prefix', 'frontend')
$runDirectory = Join-Path $projectRoot '.wealthflow/run'
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
if (!$DatabasePath) {
    $DatabasePath = if ($SmokeTest) { Join-Path $runDirectory "smoke-$stamp.db" } else { Join-Path $projectRoot 'wealthflow.db' }
}
$DatabasePath = [System.IO.Path]::GetFullPath($DatabasePath).Replace('\', '/')
$backend = $null
$frontend = $null
$oldPython = $env:WEALTHFLOW_NEWS_PYTHON
$oldTarget = $env:WEALTHFLOW_API_TARGET
try {
    $env:WEALTHFLOW_NEWS_PYTHON = $financePython
    $env:WEALTHFLOW_API_TARGET = "http://127.0.0.1:$BackendPort"
    $jar = Join-Path $projectRoot 'target/WealthFlow-0.0.1-SNAPSHOT.jar'
    $backend = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.address=127.0.0.1', "--server.port=$BackendPort", ('"--spring.datasource.url=jdbc:sqlite:' + $DatabasePath + '"')) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDirectory/backend-$stamp.log" -RedirectStandardError "$runDirectory/backend-$stamp.err.log"
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($backend.HasExited) { throw "Backend exited. See $runDirectory/backend-$stamp.log" }
        try {
            $response = Invoke-WebRequest "http://127.0.0.1:$BackendPort/api/v1/system/info" -UseBasicParsing -TimeoutSec 1
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (!$ready) { throw 'Backend did not become ready. Check the backend log.' }
    $vite = Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'
    $frontend = Start-Process -FilePath $node -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', "$FrontendPort", '--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDirectory/frontend-$stamp.log" -RedirectStandardError "$runDirectory/frontend-$stamp.err.log"
    Write-Host "WealthFlow: http://127.0.0.1:$FrontendPort   Logs: $runDirectory"
    if ($SmokeTest) {
        $frontReady = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            if ($frontend.HasExited) { throw 'Frontend exited before becoming ready.' }
            try {
                $response = Invoke-WebRequest "http://127.0.0.1:$FrontendPort/api/v1/system/info" -UseBasicParsing -TimeoutSec 1
                if ($response.StatusCode -eq 200) { $frontReady = $true; break }
            } catch { Start-Sleep -Milliseconds 500 }
        }
        if (!$frontReady) { throw 'Frontend proxy smoke test failed.' }
        Write-Host 'Smoke test passed: backend + frontend proxy, isolated database. Owned processes will stop.'
        return
    }
    Write-Host 'Keep this terminal open. Ctrl+C stops only the two processes started here.'
    while (!$backend.HasExited -and !$frontend.HasExited) { Start-Sleep -Seconds 1 }
    throw 'A WealthFlow process exited. Check the logs.'
} finally {
    foreach ($child in @($frontend, $backend)) {
        if ($null -ne $child -and !$child.HasExited) { $child.Kill(); $child.WaitForExit(5000) | Out-Null }
    }
    $env:WEALTHFLOW_NEWS_PYTHON = $oldPython
    $env:WEALTHFLOW_API_TARGET = $oldTarget
}
