$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java was not found in PATH. Install JDK 21 and try again."
}

& ".\gradlew.bat" clean build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Build complete. Extension JAR:"
Write-Host "  build/libs/BurpPostmanBridge-1.0.0.jar"
