# Set target emulator for SMS scripts
# Usage: . .\set_device.ps1 emulator-5554

param(
    [Parameter(Mandatory=$false)]
    [string]$DeviceSerial
)

if (-not $DeviceSerial) {
    # Show available devices
    Write-Host "Available emulators:" -ForegroundColor Cyan
    $ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    & $ADB devices | Select-String "emulator-" | ForEach-Object { 
        Write-Host "  $_" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "Current device: " -NoNewline -ForegroundColor Gray
    if ($env:DEVICE_SERIAL) {
        Write-Host $env:DEVICE_SERIAL -ForegroundColor Green
    } else {
        Write-Host "(auto-detect)" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "To set a specific device:" -ForegroundColor Cyan
    Write-Host '  . .\set_device.ps1 emulator-5554' -ForegroundColor Gray
    Write-Host ""
    Write-Host "To clear and use auto-detect:" -ForegroundColor Cyan
    Write-Host '  $env:DEVICE_SERIAL = $null' -ForegroundColor Gray
} else {
    $env:DEVICE_SERIAL = $DeviceSerial
    Write-Host "✓ Target device set to: $DeviceSerial" -ForegroundColor Green
    Write-Host "SMS scripts will now use this device" -ForegroundColor Gray
}
