@echo off
setlocal
set VERSION=8.9
set ROOT=%~dp0
set CACHE=%ROOT%.gradle-dist
set ZIP=%CACHE%\gradle-%VERSION%-bin.zip
set DIR=%CACHE%\gradle-%VERSION%
if not exist "%DIR%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%CACHE%' -Force"
)
call "%DIR%\bin\gradle.bat" %*
