$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
docker compose --env-file .env -f docker-compose.yml -f deploy/docker-compose.eclipse.yml stop postgres redis rabbitmq mosquitto
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure shutdown failed.' }
