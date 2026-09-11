@rem Gradle launcher for Windows. Mirrors ./gradlew; see that file for details.
@echo off
setlocal
set DIR=%~dp0
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%DIR%.gradle-home
if not "%GRADLE_HOME%"=="" (
  "%GRADLE_HOME%\bin\gradle.bat" %*
) else (
  gradle %*
)
