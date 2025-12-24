$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "Building and Installing..."
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
