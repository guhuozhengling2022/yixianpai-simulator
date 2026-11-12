param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (!(Test-Path backend/target/battle-simulator-0.1.0.jar) -or !(Test-Path frontend/dist/index.html)) {
        throw '请先执行 powershell -ExecutionPolicy Bypass -File scripts/build.ps1'
    }
    & java -jar backend/target/battle-simulator-0.1.0.jar $Port
} finally { Pop-Location }

