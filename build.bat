@echo off
setlocal
rem ============================================================
rem  G3DArmy - build G3DArmy-4.9.14.jar
rem  (v3.0.1 = HotG3DArmyTest v2.1.0 拼装兵模/主兵种选择
rem            + HotG3DArmyPseudo3DMapVersion v1.10.0 �?D地图适配)
rem ============================================================
rem --- toolchain / game root: override with JAVA_HOME / AOH_GAME_ROOT ---
if defined JAVA_HOME set "JDK=%JAVA_HOME%"
if not defined JDK for /d %%D in ("C:\Program Files\Microsoft\jdk-*") do set "JDK=%%D"
if not defined JDK for /d %%D in ("C:\Program Files\Java\jdk-*") do set "JDK=%%D"
if defined AOH_GAME_ROOT set "GAMEDIR=%AOH_GAME_ROOT%"
if not defined GAMEDIR for %%I in ("%~dp0..") do set "GAMEDIR=%%~fI"
set GAMEJAR=%GAMEDIR%\game.jar
set HOSTJAR=%GAMEDIR%\HotPluginLoader\build\libs\HotPluginLoader-0.2.0.jar

cd /d "%~dp0"

if not exist "%JDK%\bin\javac.exe" ( echo [ERR] JDK not found: %JDK% & exit /b 1 )
if not exist "%GAMEJAR%" ( echo [ERR] game.jar not found: %GAMEJAR% & exit /b 1 )
if not exist "%HOSTJAR%" ( echo [ERR] host jar not found - build HotPluginLoader first & exit /b 1 )
if not exist "libs\g3dclasses\com\badlogic\gdx\graphics\g3d" ( echo [ERR] bundled g3d classes missing: libs\g3dclasses & exit /b 1 )

echo [1/4] clean ...
if exist build rmdir /s /q build
if exist out rmdir /s /q out
mkdir build\classes
mkdir out

echo [2/4] javac ...
"%JDK%\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -cp "%HOSTJAR%;%GAMEJAR%;libs\g3dclasses" -d build\classes src\com\saysky\g3darmy\*.java
if errorlevel 1 ( echo [ERR] javac failed & exit /b 1 )

echo [3/4] bundle PC g3d classes ...
xcopy /E /I /Y /Q libs\g3dclasses build\classes >nul
if errorlevel 1 ( echo [ERR] xcopy g3d failed & exit /b 1 )

echo [4/4] jar ...
"%JDK%\bin\jar.exe" cfM out\G3DArmy-4.9.14.jar -C build\classes com
if errorlevel 1 ( echo [ERR] jar failed & exit /b 1 )

echo.
echo [OK] out\G3DArmy-4.9.14.jar
dir /b out
exit /b 0
