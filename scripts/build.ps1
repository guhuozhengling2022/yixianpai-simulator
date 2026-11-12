$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $maven = Join-Path $taskRoot 'tools/apache-maven-3.9.9/bin/mvn.cmd'
    if (!(Test-Path -LiteralPath $maven)) { $maven = 'mvn.cmd' }
    & $maven "-Dmaven.repo.local=$taskRoot/tools/m2" -f backend/pom.xml package
    if ($LASTEXITCODE -ne 0) { throw 'Java 构建失败' }
    Push-Location frontend
    try {
        if (!(Test-Path node_modules)) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    } finally { Pop-Location }
} finally { Pop-Location }

