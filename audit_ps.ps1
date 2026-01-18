$file = 'c:\Users\godha\AndroidStudioProjects\ExpenseTracker\transactions_unique_corrected.jsonl'
$allowedTypes = @('INVALID','INCOME','EXPENSE','TRANSFER','LIABILITY','INVESTMENT')
$allowedCategories = @('Food & Dining','Groceries','Fuel','Shopping','Transport','Recharge','Insurance','Investment','Bills & Utilities','Health & Fitness','Transfer','Salary','Credit Card Payment','Cash','Other')
$line = 0
Get-Content $file | ForEach-Object {
    $line++
    try {
        $obj = $_ | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Output "Line $line: Invalid JSON"
        return
    }
    if ($allowedTypes -notcontains $obj.type) {
        Write-Output "Line $line: Unexpected type $($obj.type)"
    }
    if ($allowedCategories -notcontains $obj.category) {
        Write-Output "Line $line: Unexpected category $($obj.category)"
    }
    if ([string]::IsNullOrWhiteSpace($obj.counterparty)) {
        Write-Output "Line $line: Missing/empty counterparty"
    }
}
