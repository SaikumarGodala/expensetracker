# ============================================================
# SMS Test Script - Extracts ALL messages from log file
# ============================================================
# This script reads the log JSON file and sends ALL raw messages
# to the Android emulator for testing.
#
# Usage: .\send_all_sms.ps1
# ============================================================

param(
    [string]$LogFile = "$PSScriptRoot\app\src\main\java\com\saikumar\expensetracker\log_batch_1768145796002_681b3005.json",
    [int]$DelayMs = 200,
    [int]$MaxMessages = 0  # 0 = no limit
)

Write-Host ""
Write-Host "========================================"
Write-Host " SMS Bulk Sender from Log File"
Write-Host "========================================"
Write-Host ""

$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $ADB)) {
    Write-Host "ERROR: ADB not found at $ADB" -ForegroundColor Red
    exit 1
}

# Hardcoded emulator serial
$DEVICE_SERIAL = "emulator-5554"
Write-Host "Using emulator: $DEVICE_SERIAL" -ForegroundColor Green
Write-Host ""

if (-not (Test-Path $LogFile)) {
    Write-Host "ERROR: Log file not found at $LogFile" -ForegroundColor Red
    Write-Host "Please provide a valid log file path." -ForegroundColor Yellow
    exit 1
}

Write-Host "Reading log file: $LogFile" -ForegroundColor Cyan
Write-Host "This may take a moment for large files..." -ForegroundColor Yellow

# Read and parse JSON
try {
    $logContent = Get-Content $LogFile -Raw | ConvertFrom-Json
    $totalMessages = $logContent.Count
    Write-Host "Found $totalMessages transactions in log file" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to parse JSON file: $_" -ForegroundColor Red
    exit 1
}

# Extract unique messages with their senders
$messages = @()
$seenMessages = @{}

foreach ($entry in $logContent) {
    $msg = $entry.rawInput.fullMessageText
    $sender = $entry.rawInput.sender
    
    if ($msg -and -not $seenMessages.ContainsKey($msg)) {
        $seenMessages[$msg] = $true
        $messages += @{
            Sender = if ($sender) { $sender } else { "VM-BANK" }
            Message = $msg
        }
    }
}

$uniqueCount = $messages.Count
Write-Host "Extracted $uniqueCount unique messages" -ForegroundColor Green

if ($MaxMessages -gt 0 -and $MaxMessages -lt $uniqueCount) {
    Write-Host "Limiting to first $MaxMessages messages" -ForegroundColor Yellow
    $messages = $messages | Select-Object -First $MaxMessages
}

Write-Host ""
Write-Host "Press any key to start sending messages to emulator..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

$sent = 0
$failed = 0
$startTime = Get-Date

foreach ($item in $messages) {
    $sent++
    $sender = $item.Sender
    $msg = $item.Message
    
    # Truncate long messages for display
    $displayMsg = if ($msg.Length -gt 60) { $msg.Substring(0, 60) + "..." } else { $msg }
    
    Write-Host "[$sent/$($messages.Count)] Sending from $sender" -ForegroundColor Cyan
    Write-Host "  $displayMsg" -ForegroundColor DarkGray
    
    try {
        # Send SMS without extra escaping (matches working test script)
        $result = & $ADB -s $DEVICE_SERIAL emu sms send $sender $msg 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  WARNING: Failed - $result" -ForegroundColor Yellow
            $failed++
        }
    } catch {
        Write-Host "  ERROR: $_" -ForegroundColor Red
        $failed++
    }
    
    # Delay between messages
    if ($sent -lt $messages.Count) {
        Start-Sleep -Milliseconds $DelayMs
    }
    
    # Progress indicator every 10 messages
    if ($sent % 10 -eq 0) {
        $elapsed = (Get-Date) - $startTime
        $rate = $sent / $elapsed.TotalSeconds
        $remaining = ($messages.Count - $sent) / $rate
        Write-Host "  Progress: $sent/$($messages.Count) - ETA: $([int]$remaining) seconds" -ForegroundColor DarkCyan
    }
}

$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "========================================"
Write-Host " Completed!"
Write-Host "========================================"
Write-Host ""
Write-Host "Sent: $sent messages" -ForegroundColor Green
Write-Host "Failed: $failed messages" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "Duration: $([int]$duration.TotalMinutes)m $([int]($duration.TotalSeconds % 60))s" -ForegroundColor Cyan
Write-Host ""
Write-Host "Now in the app:" -ForegroundColor Yellow
Write-Host "  1. Go to Settings"
Write-Host "  2. Tap 'Scan Inbox'"
Write-Host "  3. Check the new transactions and logs"
Write-Host ""
