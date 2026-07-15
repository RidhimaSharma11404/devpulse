@echo off
setlocal enabledelayedexpansion

echo ====================================================
echo             DevPulse Startup ^& Build Launcher       
echo ====================================================

set MVN_BIN=.maven\apache-maven-3.9.6\bin\mvn.cmd

if not exist "%MVN_BIN%" (
    echo [INFO] Local Maven not found. Downloading Apache Maven 3.9.6...
    powershell -Command "Write-Host 'Downloading Maven zip...' -ForegroundColor Cyan; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile 'maven.zip'; Write-Host 'Extracting Maven...' -ForegroundColor Cyan; Expand-Archive -Path 'maven.zip' -DestinationPath '.maven'; Remove-Item 'maven.zip'; Write-Host 'Maven downloaded and extracted successfully.' -ForegroundColor Green"
) else (
    echo [INFO] Local Maven found.
)

if not exist "temp" (
    mkdir temp
)

echo [INFO] Compiling and packaging DevPulse application...
call "%MVN_BIN%" clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed. Please check the logs.
    pause
    exit /b %ERRORLEVEL%
)

echo [INFO] Build successful! Launching DevPulse on http://localhost:8080 ...
java -jar target/devpulse-0.0.1-SNAPSHOT.jar
