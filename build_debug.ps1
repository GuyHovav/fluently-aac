$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "Checking Java Version..."
try {
    java -version
}
catch {
    Write-Host "Java check failed"
}

Write-Host "Starting Build..."
& .\gradlew assembleDebug
