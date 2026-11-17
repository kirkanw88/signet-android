$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($projectRoot -match '[^\x00-\x7f]') {
    $buildPath = 'C:\Android\signet-build'
    if (!(Test-Path -LiteralPath $buildPath)) { New-Item -ItemType Junction -Path $buildPath -Target $projectRoot | Out-Null }
    if ((Get-Item -LiteralPath $buildPath).Target -ne $projectRoot) { throw 'Existing build junction points to another project' }
} else { $buildPath = $projectRoot }
$env:GRADLE_USER_HOME = 'C:\Android\gradle-home'
Push-Location $buildPath
try {
    python scripts/fetch-native.py
    if ($LASTEXITCODE -ne 0) { throw 'Native verification failed' }
    & .\gradlew.bat :core:test :app:lintDebug :app:assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally { Pop-Location }
