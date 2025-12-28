$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"

# Check for connected devices
$devices = adb devices | Select-String -Pattern "\s+device\s*$"
if (-not $devices) {
    Write-Host "Error: No Android device found." -ForegroundColor Red
    Write-Host "Please connect a device via USB or start an Android Emulator." -ForegroundColor Yellow
    Write-Host "Debug - check adb devices output:"
    adb devices
    Read-Host "Press Enter to exit..."
    Stop-Process -Id $PID
}

Write-Host "Device found. Building and Installing..."
.\gradlew.bat installDebug


if ($LASTEXITCODE -eq 0) {
    Write-Host "Stopping app..."
    adb shell am force-stop com.example.myaac
    Write-Host "Starting app..."
    adb shell am start -n com.example.myaac/.MainActivity

    Write-Host "Build and launch successful!" -ForegroundColor Green
    
    # 10 second countdown
    for ($i = 10; $i -gt 0; $i--) {
        Write-Host -NoNewline "`rClosing in $i seconds...   "
        Start-Sleep -Seconds 1
    }
    Write-Host "`rClosing now.              "
    Stop-Process -Id $PID
}
else {
    Write-Host "Build failed." -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    Stop-Process -Id $PID
}
