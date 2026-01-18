# Comprehensive Manual-Style Transaction Categorization
# Based on pattern analysis and domain knowledge

$json = Get-Content 'final_transactions.json' -Raw | ConvertFrom-Json
$transactions = $json

Write-Host "Processing $($transactions.Count) transactions..."

$categorized = @()
$count = 0

foreach ($tx in $transactions) {
    $body = $tx.body
    $sender = $tx.sender
    
    # Initialize with defaults
    $type = "EXPENSE"
    $category = "Other"
    $counterparty = "Unknown"
    
    # === CATEGORY 1: TRANSFERS (Self-transfers between own accounts) ===
    if ($body -match 'debited from a/c \*\*4072.*to a/c \*\*(4269|5067)') {
        $type = "TRANSFER"
        $category = "Transfer"
        $accountTo = $matches[1]
        if ($accountTo -eq '4269') {
            $counterparty = "HDFC XX4072 → Union XX4269"
        } else {
            $counterparty = "HDFC XX4072 → Account XX$accountTo"
        }
    }
    
    # === CATEGORY 2: INCOME ===
    # P2P Money received
    elseif ($body -match 'credited to a/c.*by a/c linked to VPA (\S+)') {
        $type = "INCOME"
        $category = "P2P Transfer"
        $counterparty = $matches[1]
    }
    # Bank transfers received
    elseif ($body -match 'Credited for Rs\.[\d,]+.*by (Mob Bk|Cash)') {
        $type = "INCOME"
        if ($body -match 'Cash') {
            $category = "Cash Deposit"
            $counterparty = "Cash"
        } else {
            $category = "Bank Transfer"
            $counterparty = "Bank Transfer"
        }
    }
    # Salary (NEFT from employer)
    elseif ($body -match 'deposited.*NEFT.*ZF INDIA') {
        $type = "INCOME"
        $category = "Salary"
        $counterparty = "ZF INDIA PRIVATE LIMITED"
    }
    elseif ($body -match 'TATA CONSULTANCY') {
        $type = "INCOME"
        $category = "Salary"
        $counterparty = "TATA CONSULTANCY SERVICES LIMITED"
    }
    # Refunds
    elseif ($body -match 'Refund') {
        $type = "INCOME"
        $category = "Refund"
        if ($body -match 'Flipkart') {
            $counterparty = "Flipkart"
        } else {
            $counterparty = "Refund"
        }
    }
    
    # === CATEGORY 3: LIABILITY (Credit Card Payments) ===
    elseif ($body -match '(Payment|PAYMENT).*credited to your card ending (\d+)') {
        $type = "LIABILITY"
        $category = "Credit Card Payment"
        $counterparty = "Credit Card XX$($matches[2])"
    }
    elseif ($body -match 'PAYMENT.*RECEIVED TOWARDS YOUR CREDIT CARD ENDING.*?(\d+)') {
        $type = "LIABILITY"
        $category = "Credit Card Payment"
        $counterparty = "Credit Card XX$($matches[1])"
    }
    
    # === CATEGORY 4: PREPAID CARD LOADS (Transfer, not expense) ===
    elseif ($body -match 'your ICICI Bank Prepaid Card.*credited with Rs|is now active and loaded with Rs') {
        $type = "TRANSFER"
        $category = "Prepaid Card Load"
        $counterparty = "ICICI Prepaid Card XX3505"
    }
    
    # === CATEGORY 5: INVALID ===
    # Declined transactions
    elseif ($body -match 'declined|insufficient balance') {
        $type = "INVALID"
        $category = "Declined Transaction"
        $counterparty = "N/A"
    }
    # Promotional/informational
    elseif ($body -match 'Your time is precious|Do not wait till the last moment|WhatsApp.*payment|We are thrilled to launch|Complete your proposal') {
        $type = "INVALID"
        $category = "Promotional"
        $counterparty = "N/A"
    }
    # Telugu language notifications (plan expiry in Telugu)
    elseif ($body -match 'à°') {
        $type = "INVALID"
        $category = "Promotional"
        $counterparty = "N/A"
    }
    
    # === CATEGORY 6: EXPENSES ===
    # Card purchases
    elseif ($body -match 'spent.*at (.+?) on \d{4}') {
        $type = "EXPENSE"
        $merchant = $matches[1].Trim() -replace '\.\.\.|_$', ''
        $counterparty = $merchant
        
        # Categorize by merchant
        if ($merchant -match 'CREAM STONE|SUBWAY|PIZZA|NAWAABS|PISTA|KFC|Swiggy|HOUSE OF SPIRITS|DOMINOS|BUDALI|Skyrocket') {
            $category = "Food & Dining"
        }
        elseif ($merchant -match 'DMART|VIJETHA|SMPOORNA|Ratnadeep|BHARAT BAZAR|Avenue E|NAIDU GARI') {
            $category = "Groceries"
        }
        elseif ($merchant -match 'FUEL|VENKATADRI') {
            $category = "Fuel"
        }
        elseif ($merchant -match 'FLIPKART|AMAZON|Jockey|Zudio|Reliance|Diverse|Mamaearth') {
            $category = "Shopping"
        }
        elseif ($merchant -match 'TSRTC|URBANCLAP|LANDMARK TVS') {
            $category = "Transport"
        }
        elseif ($merchant -match 'TELANGANA STATE RO|PTM\*TELANGANA') {
            $category = "Bills & Utilities"
        }
        elseif ($merchant -match 'POLICYBAZAAR') {
            $category = "Insurance"
        }
        elseif ($merchant -match 'GNC|Easebuzz\*GNC') {
            $category = "Health & Fitness"
        }
        elseif ($merchant -match 'Jio Payment|REL\*Jio') {
            $category = "Recharge"
        }
        elseif ($merchant -match 'ENWAVE') {
            $category = "Other"
        }
    }
    
    # Prepaid card debits
    elseif ($body -match 'debited from ICICI Bank Prepaid Card.*Info- (.+?)\.') {
        $type = "EXPENSE"
        $merchant = $matches[1].Trim()
        $counterparty = $merchant
        
        if ($merchant -match 'CREAM STONE|SUBWAY|PIZZA|NAWAABS|PISTA|KFC|KRITUNGA|KINGS FAMILY|DADUS|NAIDU|BUDALI|Skyrocket|K165 KFC') {
            $category = "Food & Dining"
        }
        elseif ($merchant -match 'DMART|VIJETHA|SMPOORNA|AVENUE|Ratnadeep|STONE SPOT') {
            $category = "Groceries"
        }
        elseif ($merchant -match 'MANNEM RAKESH|SCRATCHBOARDS') {
            $category = "Other"
        }
    }
    
    # UPI Payments
    elseif ($body -match 'debited from (a/c|HDFC Bank).*to VPA (\S+)') {
        $type = "EXPENSE"
        $vpa = $matches[2]
        $counterparty = $vpa
        
        if ($vpa -match 'paytmqr') {
            $category = "Food & Dining"
            $counterparty = "Paytm QR"
        }
        elseif ($vpa -match 'jio@') {
            $category = "Recharge"
            $counterparty = "Jio"
        }
        elseif ($vpa -match 'upstoxsec') {
            $category = "Investment"
            $counterparty = "Upstox"
        }
        else {
            $category = "P2P Transfer"
        }
    }
    
    # Recharges
    elseif ($body -match 'Recharge of Rs\..*is successful') {
        $type = "EXPENSE"
        $category = "Recharge"
        if ($body -match 'Jio') {
            $counterparty = "Jio"
        } else {
            $counterparty = "Recharge"
        }
    }
    
    # EPF Contributions (informational, not expense)
    elseif ($body -match 'Contribution.*EPF|EPFO.*has been received') {
        $type = "INVALID"
        $category = "Informational"
        $counterparty = "EPF Notification"
    }
    
    # NPS Contributions
    elseif ($body -match 'Units against.*Contribution.*NPS|PRAN.*credited') {
        $type = "EXPENSE"
        $category = "Investment"
        $counterparty = "NPS"
    }
    
    # Insurance premiums
    elseif ($body -match 'premium.*(ICICI.*Pru|Policybazaar)|received the premium') {
        $type = "EXPENSE"
        $category = "Insurance"
        $counterparty = "ICICI Pru Life Insurance"
    }
    
    # ACH Debits (EMI/Mandates)
    elseif ($body -match 'ACH D-.*INDIAN|TP ACH INDIANESIGN') {
        $type = "EXPENSE"
        $category = "EMI/Mandate"
        $counterparty = "INDIAN CLEARING CORP"
    }
    
    # ATM Withdrawals
    elseif ($body -match 'withdrawn from.*Card') {
        $type = "EXPENSE"
        $category = "Cash Withdrawal"
        $counterparty = "ATM"
    }
    
    # Order placements
    elseif ($body -match 'Order Placed.*Mamaearth') {
        $type = "EXPENSE"
        $category = "Shopping"
        $counterparty = "Mamaearth"
    }
    
    # Government services
    elseif ($body -match 'received towards service charges.*TMESEV') {
        $type = "EXPENSE"
        $category = "Government Services"
        $counterparty = "Telangana Government"
    }
    
    # Debits without clear context
    elseif ($body -match 'Debited for Rs\.[\d,]+.*Union Bank') {
        $type = "EXPENSE"
        $category = "Other"
        $counterparty = "Union Bank Debit"
    }
    
    # Add categorized fields
    $categorized += [PSCustomObject]@{
        sender = $tx.sender
        body = $tx.body
        type = $type
        category = $category
        counterparty = $counterparty
    }
    
    $count++
    if ($count % 200 -eq 0) {
        Write-Host "Processed $count / $($transactions.Count)..."
    }
}

# Save categorized transactions
$categorized | ConvertTo-Json -Depth 10 | Set-Content 'transactions_20260112.json'

Write-Host ""
Write-Host "✅ Categorization complete!"
Write-Host "Total: $($categorized.Count) transactions"
Write-Host "Saved to: transactions_20260112.json"

# Show summary
$summary = $categorized | Group-Object -Property type | Select-Object Name, Count | Sort-Object Count -Descending
Write-Host ""
Write-Host "Summary by Type:"
$summary | Format-Table -AutoSize

$catSummary = $categorized | Group-Object -Property category | Select-Object Name, Count | Sort-Object Count -Descending
Write-Host ""
Write-Host "Top 15 Categories:"
$catSummary | Select-Object -First 15 | Format-Table -AutoSize
