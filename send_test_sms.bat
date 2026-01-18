@echo off
REM ============================================================
REM SMS Bug Fix Test Script for Android Emulator
REM ============================================================
REM This script sends test SMS messages to verify bug fixes work.
REM Run this while the emulator is running and app is open.
REM
REM Usage: Right-click this file and "Run as Administrator"
REM        Or from terminal: .\send_test_sms.bat
REM ============================================================

echo.
echo ========================================
echo  SMS Bug Fix Verification Test Suite
echo ========================================
echo.

REM Set ADB path - using Android SDK default location
set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

REM Verify ADB exists
if not exist %ADB% (
    echo ERROR: ADB not found at %ADB%
    echo Please update the ADB path in this script.
    echo.
    echo Common locations:
    echo   - %%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe
    echo   - C:\Android\sdk\platform-tools\adb.exe
    pause
    exit /b 1
)

echo Make sure:
echo  2. ExpenseTracker app is installed
echo  3. SMS permission is granted
echo.
pause

REM Set ADB path (modify if needed)
set ADB=adb

echo.
echo [1/10] Testing BUG-002: EPF Balance Notification (should be IGNORED)
%ADB% emu sms send "VM-EPFOHO" "Dear XXXXXXXX2971, your passbook balance against PUPUN**************3732 is Rs. 5,42,966/-. Contribution of Rs. 13,946/- for due month Dec-25 has been received."
timeout /t 2 >nul

echo [2/10] Testing BUG-003: Loan Spam (should be IGNORED)
%ADB% emu sms send "AX-LOAN24" "Get Rs. 241,000 credited to your bank a/c at 1% monthly interest! INSTANT Disbursal + ZERO Hidden Charges Link: https://gs.im/LOAN24"
timeout /t 2 >nul

echo [3/10] Testing BUG-004: Recharge Reminder Spam (should be IGNORED)
%ADB% emu sms send "VM-AIRTEL" "7869XXX715 par Airtel pack samapt hone wala hai! Abhi recharge karein Rs2999 mein. https://p.paytm.me/xCTH/airpld"
timeout /t 2 >nul

echo [4/10] Testing BUG-005: debited@SBI false UPI (should NOT extract as merchant)
%ADB% emu sms send "VM-SBIUPI" "Rs30.0 debited@SBI UPI frm A/cX2916 on 16Mar22 RefNo 207589475491. If not done by u, fwd this SMS to 9223008333"
timeout /t 2 >nul

echo [5/10] Testing BUG-006: P2P Transfer Out (should detect RAJEEV KUMAR)
%ADB% emu sms send "VM-SBIUPI" "Dear SBI User, your A/c X2916-debited by Rs200.0 on 24May23 transfer to RAJEEV KUMAR Ref No 314442970240"
timeout /t 2 >nul

echo [6/10] Testing BUG-006: P2P Transfer In (should detect PANDEY V)
%ADB% emu sms send "VM-SBIUPI" "Dear SBI User, your A/c X2916-credited by Rs.206.67 on 24Aug24 transfer from PANDEY V Ref No 460357016679 -SBI"
timeout /t 2 >nul

echo [7/10] Testing BUG-006: Paytm P2P (should detect Amit shukla)
%ADB% emu sms send "VK-iPaytm" "Rs. 375 transferred to Amit shukla(9589799808) at Jan 26, 2019 13:26:31. Transaction ID 22642808721, Updated Balance Rs. 48"
timeout /t 2 >nul

echo [8/10] Testing BUG-007: Home Loan EMI (should categorize as Loan EMI)
%ADB% emu sms send "JD-ICICIB" "ICICI Bank Acc XX008 debited Rs. 5,936.00 on 10-Jan-26 InfoBIL*Home Loan.Avl Bal Rs. 48,742.00"
timeout /t 2 >nul

echo [9/10] Testing BUG-001: CC Payment Receipt (should NOT be INCOME)
%ADB% emu sms send "JD-HDFCBK" "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 1820.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 1404. TOTAL OUTSTANDING IS Rs.1568.0"
timeout /t 2 >nul

echo [10/10] Testing Valid Transaction (should work correctly)
%ADB% emu sms send "VM-SBIUPI" "Rs.500 debited from A/c XX1234 to swiggy@paytm for SWIGGY order. UPI:123456789012"
timeout /t 2 >nul

echo.
echo ========================================
echo  All test messages sent!
echo ========================================
echo.
echo Now in the app:
echo  1. Go to Settings
echo  2. Tap "Scan Inbox" or "Reclassify All Transactions"
echo  3. Enable Debug Logging if not already
echo  4. Check the new log file for results
echo.
echo Expected Results:
echo  - Messages 1-3 should be IGNORED (spam filter)
echo  - Message 4 should NOT have "debited" as merchant
echo  - Messages 5-7 should detect counterparty names
echo  - Message 8 should be "Loan EMI" category
echo  - Message 9 should NOT be INCOME type
echo  - Message 10 should work as normal expense
echo.
pause
