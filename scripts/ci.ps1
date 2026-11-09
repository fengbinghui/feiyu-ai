# =====================================================================
# 本地 CI 校验脚本（Windows PowerShell）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci.ps1
# 内容：后端单元测试（mvn test）+ 前端类型检查（pnpm typecheck）
# =====================================================================
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$mvn = 'mvn'
if ($env:MVN_CMD) {
    $mvn = $env:MVN_CMD
} elseif (Test-Path 'E:\maven\apache-maven-3.8.1\bin\mvn.cmd') {
    $mvn = 'E:\maven\apache-maven-3.8.1\bin\mvn.cmd'
}

Write-Host '=== [1/2] Backend unit tests ===' -ForegroundColor Cyan
& $mvn -B -pl langchat-server -am test
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Backend tests failed.' -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host '=== [2/2] Frontend typecheck ===' -ForegroundColor Cyan
Push-Location (Join-Path $repoRoot 'langchat-ui')
try {
    & pnpm -F @vben/web-naive typecheck
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Frontend typecheck failed.' -ForegroundColor Red
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

Write-Host '=== CI checks passed ===' -ForegroundColor Green
