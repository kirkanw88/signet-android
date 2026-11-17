param([string]$Xray = 'C:\Android\signet-tools\xray-host\xray.exe')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& $Xray run -config (Join-Path $projectRoot 'tests/local-vless.json')
