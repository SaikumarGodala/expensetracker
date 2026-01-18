$ErrorActionPreference = "Stop"

$rawSmsFile = "c:\Users\godha\AndroidStudioProjects\ExpenseTracker\raw_sms_inbox_scan.jsonl.json"
$existingTxnFile = "c:\Users\godha\AndroidStudioProjects\ExpenseTracker\transactions_unique_corrected.jsonl"
$reportFile = "c:\Users\godha\AndroidStudioProjects\ExpenseTracker\sender_analysis_report.md"

Write-Host "Loading raw SMS data..."
if (-not (Test-Path $rawSmsFile)) {
    Write-Error "File not found: $rawSmsFile"
}

$rawData = @()
Get-Content -Path $rawSmsFile | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        try {
            $rawData += $_ | ConvertFrom-Json
        } catch {
            Write-Warning "Failed to parse line: $_"
        }
    }
}

Write-Host "Loaded $($rawData.Count) raw SMS messages."

$existingBodies = @{}
if (Test-Path $existingTxnFile) {
    Get-Content -Path $existingTxnFile | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            try {
                $obj = $_ | ConvertFrom-Json
                if ($obj.body) {
                    $existingBodies[$obj.body.Trim()] = $true
                }
            } catch {}
        }
    }
}

Write-Host "Loaded $($existingBodies.Count) existing transactions to ignore."

# Regex patterns
$amountRegex = "(?i)(?:rs\.?|inr)\s*[\.]?\s*([\d,]+(?:\.\d{1,2})?)"
$merchantRegex = "(?i)(?:at|to|from)\s+([a-zA-Z0-9\s\.\-\*]+?)(?:\s+(?:on|using|via|with)|$)"

$newTransactions = @()
$senderStats = @{}

Write-Host "Processing messages..."

foreach ($entry in $rawData) {
    $sender = $entry.sender
    $body = $entry.body
    
    # Sender stats
    if (-not $senderStats.ContainsKey($sender)) {
        $senderStats[$sender] = @{ Count = 0; Bodies = @() }
    }
    $senderStats[$sender].Count++
    if ($senderStats[$sender].Bodies.Count -lt 5) {
        $senderStats[$sender].Bodies += $body
    }
    
    if ([string]::IsNullOrWhiteSpace($body)) { continue }
    
    # Check duplicate
    if ($existingBodies.ContainsKey($body.Trim())) { continue }
    
    # Basic filter
    if ($body -match "(?i)(rs\.|inr|debited|credited|spent|paid|sent)") {
        
        $bodyLower = $body.ToLower()
        $txnType = "UNKNOWN"
        
        if ($bodyLower -match "credited|received|deposited") { $txnType = "INCOME" }
        elseif ($bodyLower -match "debited|spent|paid|sent") { $txnType = "EXPENSE" }
        elseif ($bodyLower -match "due|bill") { $txnType = "PENDING" }
        
        $amount = "0.00"
        if ($body -match $amountRegex) {
            $amount = $Matches[1]
        }
        
        $counterparty = "Unknown"
        if ($body -match $merchantRegex) {
            $counterparty = $Matches[1].Trim()
        }
        
        # Categories
        $category = "Other"
        if ($bodyLower -match "zomato|swiggy") { $category = "Food & Dining" }
        elseif ($bodyLower -match "uber|ola|rapido") { $category = "Transport" }
        elseif ($bodyLower -match "jio|airtel") { $category = "Recharge/Bill"; $txnType = "EXPENSE" }
        elseif ($bodyLower -match "amazon|flipkart") { $category = "Shopping" }
        elseif ($bodyLower -match "zerodha|upstox|groww") { 
            $category = "Investment"
            if ($txnType -eq "EXPENSE") { $txnType = "INVESTMENT" }
        }
        elseif ($bodyLower -match "salary") { $category = "Salary"; $txnType = "INCOME" }
        
        if ($txnType -ne "UNKNOWN") {
            $newTxn = [PSCustomObject]@{
                sender = $sender
                body = $body
                type = $txnType
                category = $category
                counterparty = $counterparty
            }
            
            $newTransactions += $newTxn
            $existingBodies[$body.Trim()] = $true
        }
    }
}

Write-Host "Found $($newTransactions.Count) new unique transactions."

if ($newTransactions.Count -gt 0) {
    $jsonLines = $newTransactions | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 10 }
    $jsonLines | Out-File -FilePath $existingTxnFile -Append -Encoding utf8
    Write-Host "Appended to $existingTxnFile"
}

# Generate Report
Write-Host "Generating report..."
$reportContent = "# Sender Pattern Analysis`n`n"
$reportContent += "| Sender | Count | Common Patterns |`n"
$reportContent += "|---|---|---|`n"

$sortedSenders = $senderStats.GetEnumerator() | Sort-Object -Property { $_.Value.Count } -Descending

foreach ($item in $sortedSenders) {
    $s = $item.Key
    $stats = $item.Value
    
    $samples = $stats.Bodies -join "<br>"
    $samples = $samples -replace "`n", " " -replace "\|", ""
    
    $reportContent += "| $s | $($stats.Count) | $samples |`n"
}

$reportContent | Out-File -FilePath $reportFile -Encoding utf8
Write-Host "Report saved to $reportFile"
