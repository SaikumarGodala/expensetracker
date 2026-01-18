
function Export-Sms-For-Emulator($name, $map) {
    Write-Host "`n=== Generating Emulator SMS Commands from $name ==="
    $outputPath = "send_to_emulator.ps1"
    $utf8 = [System.Text.Encoding]::UTF8
    
    # Create/Overwrite file
    "Write-Host 'Sending messages to emulator...'" | Out-File -FilePath $outputPath -Encoding utf8
    
    foreach ($key in $map.Keys) {
        $item = $map[$key]
        $sender = $item.rawInput.sender
        $body = $item.rawInput.fullMessageText
        
        # Clean up sender (remove spaces/special chars if any, though usually fine)
        if ([string]::IsNullOrWhiteSpace($sender)) { $sender = "123456" }
        
        # Escape for PowerShell/Shell
        # For 'adb emu', the message is a single argument. 
        # We need to replace spaces with literal spaces or keep it quoted.
        # adb emu sms send <number> <message>
        # The message argument usually stops at the first space unless quoted? 
        # Actually 'adb emu' passes the rest as the message.
        # But we need to escape double quotes in the body.
        
        $bodyEscaped = $body -replace '"', '\"'
        $bodyEscaped = $bodyEscaped -replace "'", "''" # Escape single quotes for PS string
        
        # Generate command
        # Start-Process -NoNewWindow -FilePath "adb" -ArgumentList "emu", "sms", "send", "$sender", "`"$bodyEscaped`""
        
        # Generate command with full ADB path to avoid PATH issues
        $adbPath = "C:\Users\godha\AppData\Local\Android\Sdk\platform-tools\adb.exe"
        $cmd = "& `"$adbPath`" emu sms send $sender `"$bodyEscaped`""
        $cmd | Out-File -FilePath $outputPath -Append -Encoding utf8
        
        # Add small delay to avoid overwhelming
        "Start-Sleep -Milliseconds 500" | Out-File -FilePath $outputPath -Append -Encoding utf8
    }
    
    Write-Host "Done. Commands saved to $outputPath"
}

# Update file path to the one requested
$files = @(
    @{ Name="TargetLog"; Path="app/src/main/java/com/saikumar/expensetracker/log_batch_1768145796002_681b3005.json" }
)

$data = @{}

Write-Host "Loading files..."
foreach ($f in $files) {
    Write-Host "Reading $($f.Name)..."
    try {
        $json = Get-Content $f.Path -Raw | ConvertFrom-Json
        $map = @{}
        foreach ($item in $json) {
            $key = $item.rawInput.fullMessageText
            if (-not $map.ContainsKey($key)) {
                 $map[$key] = $item
            }
        }
        $data[$f.Name] = $map
        Write-Host "Loaded $($map.Count) unique transactions for $($f.Name)."
    } catch {
        Write-Error "Failed to load $($f.Name): $_"
    }
}


function Analyze-Unknowns($name, $map) {
    Write-Host "`n=== Analyzing Unknown Transactions in $name ==="
    
    $unknowns = @{}
    
    foreach ($key in $map.Keys) {
        $item = $map[$key]
        # Check if category is "Unknown Expense" or "Unknown Income"
        # In the JSON, it might be nested under finalDecision
        $cat = $item.finalDecision.categoryName
        
        if ($cat -like "Unknown*") {
            # Try to find a distinct identifier (Merchant or start of text)
            $merchant = $item.parsedFields.merchantName
            if ([string]::IsNullOrWhiteSpace($merchant)) {
                $merchant = "NO_MERCHANT"
            }
            
            if (-not $unknowns.ContainsKey($merchant)) {
                $unknowns[$merchant] = @{ Count=0; Examples=@() }
            }
            $unknowns[$merchant].Count++
            if ($unknowns[$merchant].Examples.Count -lt 5) {
                # Add snippet/full text as example
                $snippet = $item.rawInput.fullMessageText
                if ($snippet.Length -gt 60) { $snippet = $snippet.Substring(0, 60) + "..." }
                $unknowns[$merchant].Examples += $snippet
            }
        }
    }
    
    # Sort by count descending
    $sorted = $unknowns.GetEnumerator() | Sort-Object -Property { $_.Value.Count } -Descending
    
    Write-Host "Top Unknown Patterns:"
    foreach ($row in $sorted) {
        if ($row.Value.Count -ge 1) {
            Write-Host "Count: $($row.Value.Count) | Merchant/Group: $($row.Key)"
            foreach ($ex in $row.Value.Examples) {
                Write-Host "   - $ex"
            }
            Write-Host ""
        }
    }
}

Export-Sms-For-Emulator "TargetLog" $data["TargetLog"]
Analyze-Unknowns "TargetLog" $data["TargetLog"]



