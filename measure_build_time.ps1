# Build Time Measurement Script
param(
    [string]$BuildType = "incremental"
)

# Set up environment
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build Time Measurement - $BuildType" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($BuildType -eq "clean") {
    Write-Host "Cleaning project..." -ForegroundColor Yellow
    .\gradlew.bat clean | Out-Null
    Write-Host "Clean complete. Starting timed build..." -ForegroundColor Green
    Write-Host ""
}

$startTime = Get-Date

Write-Host "Building..." -ForegroundColor Yellow
.\gradlew.bat assembleDebug --console=plain 2>&1 | Out-Null

$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BUILD COMPLETE" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build Type: $BuildType" -ForegroundColor White
Write-Host "Duration: $($duration.TotalSeconds) seconds" -ForegroundColor Yellow
Write-Host "Duration: $($duration.Minutes)m $($duration.Seconds)s" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
