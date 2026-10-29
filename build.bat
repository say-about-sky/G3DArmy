@echo off
rem ============================================================
rem  G3DArmy - thin wrapper around Gradle
rem      gradlew.bat shadowJar  -> out\G3DArmy-<version>.jar
rem  Paths: AOH_GAME_ROOT (game folder) / -PgameRoot= / -PhostJar=
rem ============================================================
setlocal
call "%~dp0gradlew.bat" shadowJar %*
set "RC=%ERRORLEVEL%"
pause
exit /b %RC%
