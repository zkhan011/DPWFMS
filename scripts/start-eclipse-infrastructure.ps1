$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path '.env')) { throw 'Missing .env. Copy .env.example to .env and replace change-me values.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required for the Eclipse infrastructure helper.' }
docker compose --env-file .env -f docker-compose.yml -f deploy/docker-compose.eclipse.yml up -d postgres redis rabbitmq mosquitto
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure startup failed.' }
Write-Host 'Infrastructure ready. In Eclipse run DpwFmsApplication with profiles dev,eclipse.'
