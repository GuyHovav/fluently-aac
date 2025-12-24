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
    exit 1
}

Write-Host "Device found. Building and Installing..."
.\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Stopping app..."
    adb shell am force-stop com.example.myaac
    Write-Host "Starting app..."
    adb shell am start -n com.example.myaac/.MainActivity
} else {
    Write-Host "Build failed."
    exit 1
}
