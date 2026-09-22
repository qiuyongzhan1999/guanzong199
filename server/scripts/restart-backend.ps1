# gz199 backend restart script
# Usage: double-click 重启后端.bat  OR  .\server\scripts\restart-backend.ps1

$ErrorActionPreference = "Stop"

$root = "D:\123\gz199"
if (-not (Test-Path (Join-Path $root "server\pom.xml"))) {
    $root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
}
$server = Join-Path $root "server"
Set-Location $server

Write-Host ""
Write-Host "======== gz199 backend restart ========" -ForegroundColor Cyan
Write-Host ("dir: " + $server)

$jdk = "E:\workTool\java\jdk-17"
$javaExe = Join-Path $jdk "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host ("JDK 17 not found: " + $jdk) -ForegroundColor Red
    Write-Host "Install Temurin JDK 17 or edit jdk path in this script."
    exit 1
}
$env:JAVA_HOME = $jdk
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

if (-not $env:DB_PASSWORD) {
    $dev = Join-Path $server "start-dev.ps1"
    if (Test-Path $dev) {
        $line = Get-Content $dev -ErrorAction SilentlyContinue | Where-Object { $_ -match "DB_PASSWORD" } | Select-Object -First 1
        if ($line -match "DB_PASSWORD\s*=\s*'([^']+)'") {
            $env:DB_PASSWORD = $Matches[1]
        } elseif ($line -match 'DB_PASSWORD\s*=\s*"([^"]+)"') {
            $env:DB_PASSWORD = $Matches[1]
        }
    }
}

# Prefer dot-sourcing local-secrets.ps1 (sets $env:DEEPSEEK_API_KEY etc.)
$secrets = Join-Path $server "local-secrets.ps1"
if (Test-Path $secrets) {
    . $secrets
    Write-Host "Loaded local-secrets.ps1" -ForegroundColor Green
}

# Load API keys: env -> local-secrets.ps1
function Import-EnvKeyFromFile([string]$path, [string]$varName) {
    if (-not (Test-Path $path)) { return $false }
    $lines = Get-Content $path -ErrorAction SilentlyContinue
    if (-not $lines) { return $false }
    foreach ($line in $lines) {
        $trim = $line.Trim()
        if (-not $trim -or $trim.StartsWith("#")) { continue }
        $pattern = [regex]::Escape('$env:' + $varName) + "\s*=\s*'([^']+)'"
        if ($trim -match $pattern) {
            Set-Item -Path ("Env:" + $varName) -Value $Matches[1].Trim()
            return $true
        }
        $pattern = [regex]::Escape('$env:' + $varName) + '\s*=\s*"([^"]+)"'
        if ($trim -match $pattern) {
            Set-Item -Path ("Env:" + $varName) -Value $Matches[1].Trim()
            return $true
        }
    }
    return $false
}

function Ensure-ApiKey([string]$varName, [string[]]$files) {
    $cur = [Environment]::GetEnvironmentVariable($varName, "Process")
    if ($cur) {
        Write-Host ("$varName : set (env, len=" + $cur.Length + ")") -ForegroundColor Green
        return $true
    }
    foreach ($cand in $files) {
        if (Import-EnvKeyFromFile $cand $varName) {
            Write-Host ("$varName : loaded from " + (Split-Path $cand -Leaf)) -ForegroundColor Green
            return $true
        }
    }
    return $false
}

$secretFiles = @(
    (Join-Path $server "local-secrets.ps1"),
    (Join-Path $server "start-dev.ps1")
)

# 择校文字建议走 DeepSeek。没配 Key 时仍可按库内分数排序。
if (-not (Ensure-ApiKey "DEEPSEEK_API_KEY" $secretFiles)) {
    Write-Host "DEEPSEEK_API_KEY : NOT set (ranking still works, no advice text)" -ForegroundColor Yellow
    Write-Host 'Add to server\local-secrets.ps1: $env:DEEPSEEK_API_KEY = "sk-..."'
}

if (-not $env:DB_PASSWORD) {
    Write-Host "DB_PASSWORD missing." -ForegroundColor Yellow
    Write-Host "Put password in server\start-dev.ps1 or run:"
    Write-Host '  $env:DB_PASSWORD = "your-password"'
    exit 1
}
Write-Host ("MySQL password: set (len=" + $env:DB_PASSWORD.Length + ")")

if ($env:DEEPSEEK_API_KEY) {
    Write-Host ("DeepSeek Key: ready (len=" + $env:DEEPSEEK_API_KEY.Length + ")") -ForegroundColor Green
} else {
    Write-Host "DeepSeek Key missing: match page ranks by score only." -ForegroundColor Yellow
}

$listen = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listen) {
    $ids = @($listen | Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($procId in $ids) {
        Write-Host ("kill pid on 8080: " + $procId)
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
} else {
    Write-Host "port 8080 is free"
}

$ErrorActionPreference = "Continue"

$verLine = (& $javaExe -version 2>&1 | Out-String)
Write-Host ("Java: " + ($verLine.Trim() -split "`n" | Select-Object -First 1))
Write-Host "Starting Spring Boot (first run may take 1-2 min)..." -ForegroundColor Cyan
Write-Host "Health check: http://127.0.0.1:8080/api/health"
Write-Host "Stop: Ctrl+C in this window"
Write-Host "======================================"
Write-Host ""

$ErrorActionPreference = "Stop"
mvn spring-boot:run
