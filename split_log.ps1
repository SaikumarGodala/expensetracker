
param(
    [string]$InputFile = "app/src/main/java/com/saikumar/expensetracker/log_batch_1768220748583_e52441f3.json"
)

$ErrorActionPreference = "Stop"

Write-Host "Reading $InputFile..." -ForegroundColor Cyan
$json = Get-Content -Path $InputFile -Raw | ConvertFrom-Json
$total = $json.Count
Write-Host "Total entries: $total" -ForegroundColor Green

$chunkSize = [Math]::Ceiling($total / 3)
Write-Host "Chunk size: ~$chunkSize" -ForegroundColor Yellow

# Part 1
$part1 = $json | Select-Object -First $chunkSize
$part1 | ConvertTo-Json -Depth 10 | Out-File -FilePath "log_part1.json" -Encoding utf8
Write-Host "Created log_part1.json ($($part1.Count) entries)"

# Part 2
$part2 = $json | Select-Object -Skip $chunkSize -First $chunkSize
$part2 | ConvertTo-Json -Depth 10 | Out-File -FilePath "log_part2.json" -Encoding utf8
Write-Host "Created log_part2.json ($($part2.Count) entries)"

# Part 3
$part3 = $json | Select-Object -Skip ($chunkSize * 2)
$part3 | ConvertTo-Json -Depth 10 | Out-File -FilePath "log_part3.json" -Encoding utf8
Write-Host "Created log_part3.json ($($part3.Count) entries)"

Write-Host "Done!" -ForegroundColor Green
