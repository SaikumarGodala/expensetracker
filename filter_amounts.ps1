# Simple filter: Get messages with Rs./Rs/INR amounts
Get-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\app\src\main\java\com\saikumar\expensetracker\raw_sms_20260112.json' -Raw | 
    ConvertFrom-Json | 
    Where-Object { 
        $_.body -match 'Rs\.' -or 
        $_.body -match ' Rs ' -or 
        $_.body -match 'INR ' 
    } | 
    ConvertTo-Json -Depth 10 | 
    Set-Content 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\messages_with_amounts.json'

Write-Host "Filtered messages saved to messages_with_amounts.json"
