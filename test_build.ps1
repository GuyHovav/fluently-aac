$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "Starting Build..."
.\gradlew.bat assembleDebug > build_debug.log 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Success!"
} else {
    Write-Host "Build Failed!"
    exit 1
}
