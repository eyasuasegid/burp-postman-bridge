@echo off
setlocal
set APP_HOME=%~dp0
java -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*
exit /b %ERRORLEVEL%
