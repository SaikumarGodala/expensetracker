# Analyze and Reduce Transaction Dataset

$corrected = Get-Content 'transactions_20260112_corrected.json' | ConvertFrom-Json

Write-Host "Total transactions in corrected file: $($corrected.Count)" -ForegroundColor Cyan
Write-Host ""

# Show category distribution
Write-Host "=== Category Distribution ===" -ForegroundColor Yellow
$corrected | Group-Object category | Select-Object Name, Count | Sort-Object Count -Descending | Format-Table -AutoSize

# Show type distribution  
Write-Host "=== Type Distribution ===" -ForegroundColor Yellow
$corrected | Group-Object type | Select-Object Name, Count | Sort-Object Count -Descending | Format-Table -AutoSize

# Identify similar transactions
Write-Host "=== Identifying Similar Patterns ===" -ForegroundColor Yellow

# Group by category + counterparty to find duplicates
$grouped = $corrected | Group-Object @{Expression={$_.category + "|" + $_.counterparty}}

Write-Host "Groups with many similar transactions:"
$grouped | Where-Object {$_.Count -gt 5} | ForEach-Object {
    Write-Host "  - $($_.Name): $($_.Count) transactions"
}

Write-Host ""
Write-Host "=== Creating Reduced Dataset ===" -ForegroundColor Cyan

$reduced = @()

# Strategy: Keep 1-2 examples from each group
foreach ($group in $grouped) {
    $examples = $group.Group
    
    if ($examples.Count -eq 1) {
        # Keep unique transactions
        $reduced += $examples[0]
    }
    elseif ($examples.Count -le 3) {
        # Keep all if small group
        $reduced += $examples
    }
    else {
        # Keep first and last (to show range)
        $reduced += $examples[0]
        if ($examples.Count -gt 2) {
            $reduced += $examples[-1]
        }
    }
}

# Save reduced dataset
$reduced | ConvertTo-Json -Depth 10 | Set-Content 'transactions_reduced.json'

Write-Host ""
Write-Host "✅ Reduced dataset created!" -ForegroundColor Green
Write-Host "Original: $($corrected.Count) transactions"
Write-Host "Reduced: $($reduced.Count) transactions"
Write-Host "Reduction: $(100 - [math]::Round(($reduced.Count / $corrected.Count) * 100, 1))%"
Write-Host ""
Write-Host "Saved to: transactions_reduced.json"
