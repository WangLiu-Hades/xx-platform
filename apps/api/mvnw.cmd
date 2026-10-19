@REM ---------------------------------------------------------------------------
@REM Maven Wrapper (lightweight equivalent; see .mvn/wrapper/maven-wrapper.properties)
@REM
@REM Usage, same as the official mvnw.cmd:
@REM   mvnw.cmd -o -B compile
@REM   mvnw.cmd -Pgenerate-api generate-sources
@REM   mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
@REM
@REM Behaviour: reuse the Maven installed on this machine; if none is found,
@REM point at the distribution cached by the wrapper (see properties file).
@REM
@REM NOTE: keep this file ASCII-only with CRLF line endings. cmd.exe parses
@REM batch files line by line and mis-reads UTF-8 text / LF-only endings.
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "DIST_DIR=%USERPROFILE%\.m2\wrapper\dists"
set "MAVEN_VERSION=3.9.16"

if exist "%WRAPPER_DIR%\maven-wrapper.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_DIR%\maven-wrapper.properties") do (
    if /i "%%A"=="mavenVersion" set "MAVEN_VERSION=%%B"
  )
)

REM 1) Reuse the Maven installed on this machine.
REM    Use "if not errorlevel 1" rather than "%ERRORLEVEL%==0": inside a
REM    parenthesised block %ERRORLEVEL% is expanded at parse time and would
REM    report the result of an earlier command.
where mvn >nul 2>nul
if not errorlevel 1 (
  call mvn %*
  exit /b %ERRORLEVEL%
)

REM 2) Fall back to a wrapper-cached distribution.
set "LOCAL_MAVEN=%DIST_DIR%\apache-maven-%MAVEN_VERSION%"
if exist "%LOCAL_MAVEN%\bin\mvn.cmd" (
  call "%LOCAL_MAVEN%\bin\mvn.cmd" %*
  exit /b %ERRORLEVEL%
)

REM 3) Neither found.
echo [mvnw] Maven not found on PATH and no wrapper distribution is cached.
echo [mvnw] Install Maven and add it to PATH, or extract the distribution
echo [mvnw] listed in .mvn\wrapper\maven-wrapper.properties into:
echo [mvnw]   %LOCAL_MAVEN%
exit /b 1
