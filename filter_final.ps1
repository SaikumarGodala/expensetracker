# Comprehensive filter: Exclude non-transactions
$messages = Get-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\app\src\main\java\com\saikumar\expensetracker\raw_sms_20260112.json' -Raw | ConvertFrom-Json

$filtered = $messages | Where-Object {
    $body = $_.body
    $sender = $_.sender
    
    # MUST have amount
    $hasAmount = $body -match 'Rs\.' -or $body -match ' Rs ' -or $body -match 'INR '
    if (-not $hasAmount) { return $false }
    
    # EXCLUDE promotional senders (Airtel, numeric IDs)
    if ($sender -eq 'Airtel') { return $false }
    if ($sender -match '^\d{6}$') { return $false }  # 6-digit numeric senders are always promotional
    
    # EXCLUDE exact match alerts
    if ($body -eq 'Alert!') { return $false }
    if ($body -eq 'Credit Alert!') { return $false }
    
    # EXCLUDE incomplete/truncated messages
    if ($body -match '^Sent Rs\.\d') { return $false }
    if ($body -match '^Amt Sent Rs\.\d') { return $false }
    if ($body -match '^Spent (INR|Rs\.?)\s*[\d,\.]+$') { return $false }
    if ($body -match '^Received (INR|Rs\.?)\s*[\d,\.]+$') { return $false }
    if ($body -match '^Credited (INR|Rs\.?)\s*[\d,\.]+$') { return $false }
    if ($body -match '^Debited (INR|Rs\.?)\s*[\d,\.]+$') { return $false }
    if ($body -match '^(IMPS|NEFT|UPI|RTGS)\s+(INR|Rs\.?)\s*[\d,\.]+$') { return $false }
    
    # EXCLUDE statement notifications (not transactions)
    if ($body -like '*Statement is sent to*') { return $false }
    if ($body -like '*statement*due by*') { return $false }
    
    # EXCLUDE promotional/store credits (fake money)
    if ($body -like '*credited in your Lenskart account*') { return $false }
    if ($body -like '*credited till*') { return $false }
    if ($body -like '*Cash expires*') { return $false }
    
    # EXCLUDE reminders/upcoming (not actual transactions yet)
    if ($body -like '*Upcoming mandate*') { return $false }
    if ($body -like '*will be debited*') { return $false }
    
    # EXCLUDE promotional offers
    if ($body -like '*CASHBACK*') { return $false }
    if ($body -like '*Extra*GB FREE*') { return $false }
    
    # EXCLUDE AutoPay/money requests
    if ($body -like '*AutoPay request*') { return $false }
    if ($body -like '*requested money from you*') { return $false }
    
    # EXCLUDE plan expiry warnings
    if ($body -like '*will expire on*') { return $false }
    if ($body -like '*has expired on*') { return $false }
    if ($body -like '*plan expiry*') { return $false }
    
    return $true
}

$filtered | ConvertTo-Json -Depth 10 | Set-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\final_transactions.json'

Write-Host "Total messages: $($messages.Count)"
Write-Host "Final transactions: $($filtered.Count)" 
Write-Host "Filtered out: $($messages.Count - $filtered.Count) messages"
Write-Host ""
Write-Host "Saved to final_transactions.json"
