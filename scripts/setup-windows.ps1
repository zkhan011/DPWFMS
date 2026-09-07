. "$PSScriptRoot/common-windows.ps1"; 'java','mvn','node','npm','psql','pg_isready'|ForEach-Object{Assert-Command $_};Assert-Java21;Import-DpwEnv;Assert-Database
Push-Location "$Root/fms-web";npm install;npm run build;Pop-Location
Push-Location $Root;& .\mvnw.cmd -B clean verify;Pop-Location
Write-Host 'Setup complete. Run .\scripts\start-windows.ps1 or .\scripts\dev-windows.ps1'
