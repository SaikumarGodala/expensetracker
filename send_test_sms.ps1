# ============================================================
# SMS Bug Fix Test Script for Android Emulator (PowerShell)
# ============================================================
# Usage: .\send_test_sms.ps1
# ============================================================

Write-Host ""
Write-Host "========================================"
Write-Host " SMS Bug Fix Verification Test Suite"
Write-Host "========================================"
Write-Host ""

$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $ADB)) {
    Write-Host "ERROR: ADB not found at $ADB" -ForegroundColor Red
    exit 1
}

# Auto-detect emulator or use specified device
$DEVICE_SERIAL = $env:DEVICE_SERIAL
if (-not $DEVICE_SERIAL) {
    Write-Host "Detecting available devices..." -ForegroundColor Cyan
    $devices = & $ADB devices | Select-String "emulator-" | ForEach-Object { $_.ToString().Split()[0] }
    
    if ($devices.Count -eq 0) {
        Write-Host "ERROR: No emulators detected. Please start an emulator first." -ForegroundColor Red
        exit 1
    } elseif ($devices.Count -eq 1) {
        $DEVICE_SERIAL = $devices[0]
        Write-Host "Using emulator: $DEVICE_SERIAL" -ForegroundColor Green
    } else {
        $DEVICE_SERIAL = $devices[0]
        Write-Host "Multiple emulators detected. Using first: $DEVICE_SERIAL" -ForegroundColor Yellow
        Write-Host "Available devices:" -ForegroundColor Yellow
        $devices | ForEach-Object { Write-Host "  - $_" -ForegroundColor Gray }
        Write-Host "To use a specific device, set environment variable:" -ForegroundColor Yellow
        Write-Host '  $env:DEVICE_SERIAL = "emulator-XXXX"' -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Sending test SMS messages..." -ForegroundColor Green
Write-Host ""

# Test 1: BUG-002 - EPF Balance (should be IGNORED)
Write-Host "[1/10] BUG-002: EPF Balance Notification (should be IGNORED)"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-EPFOHO" "Dear XXXXXXXX2971, your passbook balance against PUPUN is Rs. 5,42,966/-. Contribution of Rs. 13,946/- for due month Dec-25 has been received."
Start-Sleep -Seconds 2

# Test 2: BUG-003 - Loan Spam (should be IGNORED)
Write-Host "[2/10] BUG-003: Loan Spam (should be IGNORED)"
& $ADB -s $DEVICE_SERIAL emu sms send "AX-LOAN24" "Get Rs. 241,000 credited to your bank a/c at 1% monthly interest! INSTANT Disbursal + ZERO Hidden Charges Link: https://gs.im/LOAN24"
Start-Sleep -Seconds 2

# Test 3: BUG-004 - Recharge Reminder (should be IGNORED)
Write-Host "[3/10] BUG-004: Recharge Reminder (should be IGNORED)"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-AIRTEL" "7869XXX715 par Airtel pack samapt hone wala hai! Abhi recharge karein Rs2999 mein. https://p.paytm.me/xCTH/airpld"
Start-Sleep -Seconds 2

# Test 4: BUG-005 - debited@SBI false UPI
Write-Host "[4/10] BUG-005: debited@SBI (should NOT be merchant name)"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-SBIUPI" "Rs30.0 debited@SBI UPI frm A/cX2916 on 16Mar22 RefNo 207589475491. If not done by u, fwd this SMS to 9223008333"
Start-Sleep -Seconds 2

# Test 5: BUG-006 - P2P Transfer Out
Write-Host "[5/10] BUG-006: P2P Transfer to RAJEEV KUMAR"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-SBIUPI" "Dear SBI User, your A/c X2916-debited by Rs200.0 on 24May23 transfer to RAJEEV KUMAR Ref No 314442970240"
Start-Sleep -Seconds 2

# Test 6: BUG-006 - P2P Transfer In
Write-Host "[6/10] BUG-006: P2P Transfer from PANDEY V"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-SBIUPI" "Dear SBI User, your A/c X2916-credited by Rs.206.67 on 24Aug24 transfer from PANDEY V Ref No 460357016679 -SBI"
Start-Sleep -Seconds 2

# Test 7: BUG-006 - Paytm P2P
Write-Host "[7/10] BUG-006: Paytm P2P to Amit shukla"
& $ADB -s $DEVICE_SERIAL emu sms send "VK-iPaytm" "Rs. 375 transferred to Amit shukla(9589799808) at Jan 26, 2019 13:26:31. Transaction ID 22642808721"
Start-Sleep -Seconds 2

# Test 8: BUG-007 - Home Loan EMI
Write-Host "[8/10] BUG-007: Home Loan EMI (should be Loan EMI category)"
& $ADB -s $DEVICE_SERIAL emu sms send "JD-ICICIB" "ICICI Bank Acc XX008 debited Rs. 5,936.00 on 10-Jan-26 InfoBIL*Home Loan.Avl Bal Rs. 48,742.00"
Start-Sleep -Seconds 2

# Test 9: BUG-001 - CC Payment Receipt
Write-Host "[9/10] BUG-001: CC Payment Receipt (should NOT be INCOME)"
& $ADB -s $DEVICE_SERIAL emu sms send "JD-HDFCBK" "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 1820.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 1404."
Start-Sleep -Seconds 2

# Test 10: Valid Transaction
Write-Host "[10/10] Valid: Normal expense transaction"
& $ADB -s $DEVICE_SERIAL emu sms send "VM-SBIUPI" "Rs.500 debited from A/c XX1234 to swiggy@paytm for SWIGGY order. UPI:123456789012"
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "========================================"
Write-Host " All test messages sent!"
Write-Host "========================================"
Write-Host ""
Write-Host "Now in the app:" -ForegroundColor Yellow
Write-Host "  1. Go to Settings"
Write-Host "  2. Tap 'Scan Inbox'"
Write-Host "  3. Check the transactions list"
Write-Host ""
