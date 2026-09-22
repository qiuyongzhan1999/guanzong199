# Smoke: health, school detail (no enrich), match ranking
$ErrorActionPreference = "Continue"
$base = "http://127.0.0.1:8080/api"

Write-Host "GET /health"
try {
    $h = Invoke-RestMethod "$base/health" -TimeoutSec 10
    $h | ConvertTo-Json -Compress
    if (-not $h.ok) { Write-Host "FAIL health" -ForegroundColor Red; exit 1 }
    if ($h.deepseekConfigured -ne $true) { Write-Host "FAIL: DeepSeek key not loaded" -ForegroundColor Red; exit 1 }
} catch {
    Write-Host ("FAIL health: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "GET /school-detail"
$url = "$base/school-detail?schoolCode=10001&year=2026&majorCode=125300&studyMode=fulltime&province=%E5%8C%97%E4%BA%AC"
try {
    $d = Invoke-RestMethod $url -TimeoutSec 20
    if ($null -ne $d.enrich) { Write-Host "FAIL: detail still returns enrich" -ForegroundColor Red; exit 1 }
    Write-Host ("source=" + $d.source + " years=" + (($d.yearlyData | Measure-Object).Count))
} catch {
    Write-Host ("FAIL detail: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "POST /match"
$json = '{"majorCode":"125300","studyMode":"fulltime","scoreMin":200,"scoreMax":230,"provinces":["\u5317\u4eac"]}'
$tmp = Join-Path $env:TEMP "gz199-match-body.json"
$out = Join-Path $env:TEMP "gz199-match-out.json"
[System.IO.File]::WriteAllText($tmp, $json, [System.Text.UTF8Encoding]::new($false))
try {
    curl.exe -s -m 120 -X POST "$base/match" -H "Content-Type: application/json" --data-binary "@$tmp" -o $out
    if ($LASTEXITCODE -ne 0) { Write-Host "FAIL curl" -ForegroundColor Red; exit 1 }
    $text = [System.IO.File]::ReadAllText($out, [System.Text.UTF8Encoding]::new($false))
    if ($text -notmatch '"rankedCount":(\d+)') { Write-Host "FAIL: no rankedCount" -ForegroundColor Red; exit 1 }
    $n = [int]$Matches[1]
    $un = 0
    if ($text -match '"unmatchedCount":(\d+)') { $un = [int]$Matches[1] }
    Write-Host ("ranked=" + $n + " unmatched=" + $un)
    if ($n -lt 2) { Write-Host "FAIL: expected several schools" -ForegroundColor Red; exit 1 }
    $gaps = [regex]::Matches($text, '"gap":(-?\d+)') | ForEach-Object { [int]$_.Groups[1].Value }
    if ($gaps.Count -lt 2) { Write-Host "FAIL: gaps missing" -ForegroundColor Red; exit 1 }
    if ($gaps[0] -lt $gaps[$gaps.Count - 1]) { Write-Host "FAIL: not sorted easy to hard" -ForegroundColor Red; exit 1 }
    Write-Host ("gap first=" + $gaps[0] + " last=" + $gaps[$gaps.Count - 1]) -ForegroundColor Green
    $titles = [regex]::Matches($text, '"title":"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
    Write-Host ("adviceBlocks=" + ($titles -join ","))
    if (-not $titles -or $titles.Count -lt 1) { Write-Host "FAIL: no advice layout" -ForegroundColor Red; exit 1 }
    if ($text -notmatch '"advice":"') { Write-Host "FAIL: empty advice" -ForegroundColor Red; exit 1 }
    Write-Host "OK" -ForegroundColor Green
} finally {
    Remove-Item $tmp, $out -ErrorAction SilentlyContinue
}
