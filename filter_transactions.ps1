# Enhanced filter with stricter conditions
$messages = Get-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\app\src\main\java\com\saikumar\expensetracker\raw_sms_20260112.json' -Raw | ConvertFrom-Json

$filtered = $messages | Where-Object {
    $body = $_.body
    
    # MUST have amount
    $hasAmount = $body -match 'Rs\.' -or $body -match ' Rs ' -or $body -match 'INR '
    
    if (-not $hasAmount) { return $false }
    
    # EXCLUDE exact match alerts
    if ($body -eq 'Alert!') { return $false }
    if ($body -eq 'Credit Alert!') { return $false }
    
    # EXCLUDE AutoPay/money requests
    if ($body -like '*AutoPay request*') { return $false }
    if ($body -like '*requested money from you*') { return $false }
    
    # EXCLUDE plan expiry warnings
    if ($body -like '*will expire on*') { return $false }
    if ($body -like '*has expired on*') { return $false }
    if ($body -like '*plan expiry*') { return $false }
    
    return $true
}

$filtered | ConvertTo-Json -Depth 10 | Set-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\filtered_transactions.json'

Write-Host "Total messages: $($messages.Count)"
Write-Host "Filtered transactions: $($filtered.Count)" 
Write-Host "Saved to filtered_transactions.json"
