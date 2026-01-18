$logDir = "$PSScriptRoot\app\src\main\java\com\saikumar\expensetracker"
$logFiles = Get-ChildItem -Path $logDir -Filter "log_batch*.json"

$suspiciousKeywords = @(
    "failed", "declined", "unsuccessful", 
    "request", "requested", "autopay request",
    "due", "overdue", "bill due",
    "limit", "credit limit", "available limit",
    "offer", "eligible", "congratulations",
    "switch to", "plan", "benefit",
    "refund", "reversed",
    "recharge of", "validity"
)

$count = 0
$processedFiles = 0

foreach ($file in $logFiles) {
    Write-Host "Scanning $($file.Name)..." -ForegroundColor Cyan
    try {
        $data = Get-Content $file.FullName -Raw | ConvertFrom-Json
        $processedFiles++
    } catch {
        Write-Host "Error reading $($file.Name)" -ForegroundColor Red
        continue
    }


    foreach ($entry in $data) {
        # Debug: Print first few messages to ensure we are reading valid data
        if ($processedFiles -eq 1 -and $count -lt 3) {
             # Force print to Prove data is there
             Write-Host "DEBUG SAMPLE: $($entry.rawInput.fullMessageText.Substring(0, 20))..." -ForegroundColor DarkGray
        }

        $body = $entry.rawInput.fullMessageText
        $isValid = $entry.finalDecision.isValidTransaction
        
        # KEYWORD MATCHING
        foreach ($keyword in $suspiciousKeywords) {
            # Use -match for case-insensitive regex
            if ($body -match [regex]::Escape($keyword)) {
                
                # Filter out obvious legit ones only if we are sure
                if ($entry.finalDecision.categoryName -eq "Wallet Topup" -and ($keyword -eq "topup" -or $keyword -eq "recharge of")) {
                    continue
                }

                # SHOW EVERYTHING VALID for now to find the leaks
                if ($isValid -eq $true) {
                    Write-Host "`n[$($file.Name)]" -ForegroundColor DarkGray
                    Write-Host "TYPE: [$($entry.finalDecision.transactionType)] CAT: $($entry.finalDecision.categoryName)" -ForegroundColor Yellow
                    Write-Host "MATCH: '$keyword'" -ForegroundColor Red
                    Write-Host "MSG: $($entry.rawInput.fullMessageText)" -ForegroundColor Gray
                    
                    $count++
                    break 
                }
            }
        }
    }
}
Write-Host "`nScanned $processedFiles files."

Write-Host "`nFound $count potentially misleading active transactions." -ForegroundColor Green
