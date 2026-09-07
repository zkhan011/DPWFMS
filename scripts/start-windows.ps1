. "$PSScriptRoot/common-windows.ps1";Import-DpwEnv;Assert-Java21;Assert-Database
$jar=Join-Path $Root 'fms-api/target/fms-api-1.0.0-SNAPSHOT.jar';if(!(Test-Path $jar)){Push-Location $Root;& .\mvnw.cmd -B -DskipTests package;Pop-Location}
$process=Start-Process java -ArgumentList '-jar',"`"$jar`"",'--spring.profiles.active=prod' -RedirectStandardOutput "$Logs/backend.log" -RedirectStandardError "$Logs/backend-error.log" -PassThru
$process.Id|Set-Content "$Runtime/backend.pid"
$dist=Join-Path $Root 'fms-web/dist';if(Test-Path $dist){$frontend=Start-Process npm -WorkingDirectory "$Root/fms-web" -ArgumentList 'run','preview','--','--host','0.0.0.0','--port','3000' -RedirectStandardOutput "$Logs/frontend.log" -RedirectStandardError "$Logs/frontend-error.log" -PassThru;$frontend.Id|Set-Content "$Runtime/frontend.pid"}
Write-Host "DPW FMS started: frontend http://localhost:3000, API http://localhost:8080"
