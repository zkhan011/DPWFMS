$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Runtime = Join-Path $Root 'runtime'; $Logs = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force $Runtime,$Logs | Out-Null
function Import-DpwEnv { $file=Join-Path $Root '.env'; if(!(Test-Path $file)){throw "Missing $file. Copy .env.example to .env and set secrets."}; Get-Content $file | Where-Object {$_ -match '^[^#][^=]+='} | ForEach-Object {$key,$value=$_.Split('=',2);[Environment]::SetEnvironmentVariable($key.Trim(),$value.Trim(),'Process')} }
function Assert-Command([string]$Name){if(!(Get-Command $Name -ErrorAction SilentlyContinue)){throw "Required command '$Name' was not found. Install it, then retry."}}
function Assert-Java21 { $version=& java -version 2>&1 | Select-Object -First 1;if($version -notmatch 'version "21'){throw "Java 21 is required; found $version"} }
function Assert-Database { & pg_isready -d $env:DB_URL -U $env:DB_USER | Out-Null;if($LASTEXITCODE -ne 0){throw "PostgreSQL is unreachable at $env:DB_URL"} }
