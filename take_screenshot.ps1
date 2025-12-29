$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

# Take screenshot on device
& $adb shell screencap -p /sdcard/screenshot.png

# Pull screenshot to local machine
& $adb pull /sdcard/screenshot.png .

# Remove screenshot from device
& $adb shell rm /sdcard/screenshot.png

Write-Host "Screenshot saved to screenshot.png"
