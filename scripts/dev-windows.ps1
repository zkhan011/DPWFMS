. "$PSScriptRoot/common-windows.ps1";Import-DpwEnv;Assert-Java21;Assert-Database
$backend=Start-Process mvn -WorkingDirectory $Root -ArgumentList '-pl','fms-api','-am','spring-boot:run','-Dspring-boot.run.profiles=dev' -RedirectStandardOutput "$Logs/backend-dev.log" -RedirectStandardError "$Logs/backend-dev-error.log" -PassThru
$frontend=Start-Process npm -WorkingDirectory "$Root/fms-web" -ArgumentList 'run','dev' -RedirectStandardOutput "$Logs/frontend-dev.log" -RedirectStandardError "$Logs/frontend-dev-error.log" -PassThru
$backend.Id|Set-Content "$Runtime/backend.pid";$frontend.Id|Set-Content "$Runtime/frontend.pid";Write-Host 'Frontend: http://localhost:5173  API: http://localhost:8080';Write-Host 'Run .\scripts\stop-windows.ps1 to stop.'
