$ErrorActionPreference = "Stop"

$txnFile = "c:\Users\godha\AndroidStudioProjects\ExpenseTracker\transactions_unique_corrected.jsonl"
$backupFile = "c:\Users\godha\AndroidStudioProjects\ExpenseTracker\transactions_unique_corrected.jsonl.bak"

Write-Host "Backing up file..."
Copy-Item $txnFile $backupFile -Force

Write-Host "Loading transactions..."
$data = @()
Get-Content -Path $txnFile | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        try {
            $data += $_ | ConvertFrom-Json
        } catch {
            Write-Warning "Failed to parse line: $_"
        }
    }
}

Write-Host "Loaded $($data.Count) transactions. Re-categorizing..."

$updatedData = @()

foreach ($entry in $data) {
    $sender = $entry.sender
    $body = $entry.body
    $bodyLower = $body.ToLower()
    
    # Default values (preserve existing if sensible, but we generally want to overwrite 'Other' or 'Unknown')
    $type = $entry.type
    $category = $entry.category
    $counterparty = $entry.counterparty

    # --- Heuristic Rules ---

    # 1. Investments (High Priority)
    if ($bodyLower -match "zerodha|upstox|groww|kite|cdsl|nsdl|mutual fund|sip|folio|nav") {
        $category = "Investment"
        if ($bodyLower -match "debited|spent|paid") { $type = "INVESTMENT" }
        elseif ($bodyLower -match "credited|received") { $type = "INCOME" } # Dividend/Redemption
        $counterparty = if ($bodyLower -match "zerodha") { "Zerodha" } elseif ($bodyLower -match "groww") { "Groww" } else { "Investment" }
    }
    elseif ($bodyLower -match "pran\s+|nps|protean") {
        $category = "Investment"
        $type = "INVESTMENT" 
        $counterparty = "NPS (Protean)"
    }
    elseif ($bodyLower -match "epfo|provident fund|pension") {
        $category = "Investment" 
        $type = "INFO" # Usually just info updates
        $counterparty = "EPF"
    }

    # 2. Credit Card Payments (Liability)
    elseif ($bodyLower -match "payment.*received.*credit card|credited.*card.*ending|bill.*paid") {
        $category = "Credit Card Payment"
        $type = "LIABILITY"
        $counterparty = "Credit Card Bill"
    }
    elseif ($sender -match "CRED" -or $bodyLower -match "cred.*payment") {
        $category = "Credit Card Payment"
        $type = "LIABILITY"
        $counterparty = "CRED"
    }

    # 3. Food & Dining (Strict)
    elseif ($bodyLower -match "swiggy|zomato|domino|pizza hut|kfc|mcdonald|burger king|restaurant|dining|cafe|coffee|starbucks|tea") {
        $category = "Food & Dining"
        $type = "EXPENSE"
        $counterparty = if ($bodyLower -match "swiggy") { "Swiggy" } elseif ($bodyLower -match "zomato") { "Zomato" } else { "Restaurant" }
    }

    # 4. Groceries / Daily Needs
    elseif ($bodyLower -match "blinkit|zepto|bigbasket|dmart|ratnadeep|reliance fresh|instamart|grocery|supermarket|more retail|spencer") {
        $category = "Groceries"
        $type = "EXPENSE"
        $counterparty = "Grocery Store"
    }

    # 5. Transport / Fuel
    elseif ($bodyLower -match "uber|ola|rapido|irctc|redbus|metro|tsrtc|apsrtc|telangana state road|fuel|petrol|diesel|shell|hpcl|bpcl|ioc|toll|fastag") {
        $category = "Transport"
        $type = "EXPENSE"
        $counterparty = "Transport/Fuel"
    }

    # 6. Shopping
    elseif ($bodyLower -match "amazon|flipkart|myntra|ajio|tata cliq|decathlon|mamaearth|nykaa|shopping|lifestyle|pantaloons|trends|max") {
        $category = "Shopping"
        $type = "EXPENSE"
        $counterparty = if ($bodyLower -match "amazon") { "Amazon" } elseif ($bodyLower -match "flipkart") { "Flipkart" } else { "Shopping" }
    }

    # 7. Bills & Utilities
    elseif ($bodyLower -match "airtel|jio|vi |vodafone|bsnl|act fibernet|bescom|electricity|power|gas|water|billdesk|recharge|postpaid|broadband|dth|tatasky") {
        $category = "Bills & Utilities"
        $type = "EXPENSE"
        $counterparty = "Utility Provider"
    }
     # 8. Medical
    elseif ($bodyLower -match "apollo|pharmeasy|medplus|netmeds|1mg|hospital|clinic|doctor|pharmacy|labs|diagnostic") {
        $category = "Health & Medical"
        $type = "EXPENSE"
        $counterparty = "Medical"
    }

    # 9. P2P / UPI
    elseif ($bodyLower -match "vpa|upi|gpay|phonepe|paytm|bhim") {
         if ($bodyLower -match "credited|received") {
             $category = "P2P Transfer"
             $type = "INCOME"
         } elseif ($bodyLower -match "debited|paid|sent") {
             $category = "P2P Transfer"
             $type = "EXPENSE"
         }
    }
    
    # 10. Salary
    elseif ($bodyLower -match "salary|neft.*credited") {
         $category = "Salary"
         $type = "INCOME"
    }

    # Update the object
    $entry.category = $category
    $entry.type = $type
    if ($counterparty -ne $entry.counterparty -and $counterparty -ne $null) {
        $entry.counterparty = $counterparty
    }

    $updatedData += $entry
}

Write-Host "Saving updated transactions..."
$updatedData | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 10 } | Out-File -FilePath $txnFile -Encoding utf8

Write-Host "Done. Backup saved at $backupFile"
