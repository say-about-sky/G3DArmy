#
#  G3DArmy - one-shot build
#
#    powershell -ExecutionPolicy Bypass -File build.ps1
#    powershell -ExecutionPolicy Bypass -File build.ps1 -GameRoot <game dir>
#
#  Steps: resolve game root / JDK -> ensure host jar (HotPluginLoader) -> ensure libs\g3dclasses
#         -> run build.bat -> out\G3DArmy-<ver>.jar
#
param(
    [string]$GameRoot = "",
    [string]$JdkHome = "",
    [string]$HostJar = "",
    [string]$GdxVersion = "1.12.1"
)
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$proj = $PSScriptRoot

function Fail([string]$m) { Write-Host ("[ERROR] " + $m); exit 1 }
function Get-JdkMajor([string]$jdkDir) {
    $rel = Join-Path $jdkDir "release"
    if (Test-Path -LiteralPath $rel) {
        $m = Select-String -LiteralPath $rel -Pattern 'JAVA_VERSION="(\d+)' | Select-Object -First 1
        if ($m) { return [int]$m.Matches[0].Groups[1].Value }
    }
    return 0
}

if ([string]::IsNullOrEmpty($GameRoot)) {
    if ($env:AOH_GAME_ROOT) { $GameRoot = $env:AOH_GAME_ROOT } else { $GameRoot = Split-Path -Parent $proj }
}
Write-Host "=== game root: $GameRoot"
if (-not (Test-Path -LiteralPath (Join-Path $GameRoot "game.jar"))) {
    Fail "game.jar not found in '$GameRoot' (pass -GameRoot or set AOH_GAME_ROOT)"
}

if ([string]::IsNullOrEmpty($JdkHome)) {
    $cands = New-Object System.Collections.ArrayList
    if ($env:JAVA_HOME) { $cands.Add($env:JAVA_HOME) | Out-Null }
    Get-ChildItem "C:\Program Files\Microsoft\jdk-*","C:\Program Files\Java\jdk-*" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $cands.Add($_.FullName) | Out-Null }
    foreach ($c in $cands) {
        if (-not (Test-Path -LiteralPath (Join-Path $c "bin\javac.exe"))) { continue }
        $major = Get-JdkMajor $c
        if ($major -ge 11 -and $major -le 17) { $JdkHome = $c; break }
    }
}
if ([string]::IsNullOrEmpty($JdkHome)) { Fail "JDK 11+ not found: set JAVA_HOME or pass -JdkHome" }
Write-Host "=== jdk      : $JdkHome"

# ---- host jar (HotPluginLoader) ----
function Find-HostJar {
    $hit = @(Get-ChildItem -LiteralPath (Join-Path $GameRoot "HotPluginLoader\build\libs") -Filter "HotPluginLoader-*.jar" -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1)
    if ($hit.Count -gt 0) { return $hit[0].FullName }
    return $null
}
if ([string]::IsNullOrEmpty($HostJar)) { $HostJar = Find-HostJar }
if ([string]::IsNullOrEmpty($HostJar)) {
    $hostPs1 = Join-Path $GameRoot "HotPluginLoader\build.ps1"
    if (Test-Path -LiteralPath $hostPs1) {
        Write-Host "=== host jar missing, building HotPluginLoader (jar only) ..."
        & powershell -NoProfile -ExecutionPolicy Bypass -File $hostPs1 -JarOnly -GameRoot $GameRoot -JdkHome $JdkHome
        $HostJar = Find-HostJar
    }
}
if ([string]::IsNullOrEmpty($HostJar)) { Fail "HotPluginLoader jar not found: build it first (HotPluginLoader\build.bat) or pass -HostJar" }
Write-Host "=== host jar : $HostJar"

# ---- libs\g3dclasses (extracted from the official libGDX jar) ----
$g3d = Join-Path $proj "libs\g3dclasses"
if (-not (Test-Path -LiteralPath (Join-Path $g3d "com\badlogic\gdx\graphics\g3d"))) {
    Write-Host "=== g3dclasses missing, extracting com/badlogic/gdx/graphics/g3d from gdx $GdxVersion ..."
    $tmp = Join-Path $env:TEMP ("gdx-" + $GdxVersion + ".jar")
    $urls = @(
        "https://maven.aliyun.com/repository/public/com/badlogicgames/gdx/gdx/$GdxVersion/gdx-$GdxVersion.jar",
        "https://repo1.maven.org/maven2/com/badlogicgames/gdx/gdx/$GdxVersion/gdx-$GdxVersion.jar"
    )
    $ok = $false
    foreach ($u in $urls) {
        try { Invoke-WebRequest -Uri $u -OutFile $tmp -UseBasicParsing; $ok = $true; break } catch { }
    }
    if (-not $ok) { Fail "cannot download gdx $GdxVersion (need libs\g3dclasses\com\badlogic\gdx\graphics\g3d)" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (-not (Test-Path -LiteralPath $g3d)) { New-Item -ItemType Directory -Path $g3d | Out-Null }
    $zip = [IO.Compression.ZipFile]::OpenRead($tmp)
    try {
        foreach ($e in $zip.Entries) {
            if ($e.FullName -notlike 'com/badlogic/gdx/graphics/g3d/*') { continue }
            if ([string]::IsNullOrEmpty($e.Name)) { continue }
            $dest = Join-Path $g3d ($e.FullName -replace '/', '\')
            $dir = Split-Path -Parent $dest
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
        }
    } finally { $zip.Dispose() }
    Write-Host "=== g3dclasses ready"
}

# ---- run build.bat ----
$env:AOH_GAME_ROOT = $GameRoot
$env:HOSTJAR = $HostJar
$env:JAVA_HOME = $JdkHome
Write-Host "=== build.bat"
& cmd /c "`"$proj\build.bat`""
if ($LASTEXITCODE -ne 0) { Fail "build.bat failed" }

$jar = @(Get-ChildItem -LiteralPath (Join-Path $proj "out") -Filter "*.jar" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
if ($jar.Count -eq 0) { Fail "no jar produced under out\" }
Write-Host ""
Write-Host ("[OK] " + $jar[0].FullName + "  (" + $jar[0].Length + " bytes)")
exit 0
