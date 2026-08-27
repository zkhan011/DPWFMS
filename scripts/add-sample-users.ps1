$ErrorActionPreference = 'Stop'
if (-not $env:DPWFMS_LOCAL_USERNAME) { throw 'Set DPWFMS_LOCAL_USERNAME to an administrator username.' }
if (-not $env:DPWFMS_LOCAL_PASSWORD) { throw 'Set DPWFMS_LOCAL_PASSWORD to the administrator password.' }
if (-not $env:DPWFMS_SAMPLE_USER_PASSWORD) { throw 'Set DPWFMS_SAMPLE_USER_PASSWORD (minimum 12 characters).' }
$apiUrl = if ($env:DPWFMS_API_URL) { $env:DPWFMS_API_URL.TrimEnd('/') } else { 'http://localhost:8080' }
$pair = "$($env:DPWFMS_LOCAL_USERNAME):$($env:DPWFMS_LOCAL_PASSWORD)"
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }
$roles = Invoke-RestMethod -Uri "$apiUrl/api/admin/roles" -Headers $headers
$plantIds = @((Invoke-RestMethod -Uri "$apiUrl/api/workspace/plants" -Headers $headers) | ForEach-Object { $_.id })
@(
  @{ username='dispatcher.demo'; displayName='Demo Dispatcher'; role='DISPATCHER' },
  @{ username='operator.demo'; displayName='Demo Control Room Operator'; role='CONTROL_ROOM_OPERATOR' },
  @{ username='viewer.demo'; displayName='Demo Report Viewer'; role='REPORT_VIEWER' }
) | ForEach-Object {
  $sample = $_; $roleId = ($roles | Where-Object name -eq $sample.role).id
  $body = @{ username=$sample.username; displayName=$sample.displayName; password=$env:DPWFMS_SAMPLE_USER_PASSWORD;
    serviceAccount=$false; roleIds=@($roleId); plantIds=$plantIds } | ConvertTo-Json
  try {
    Invoke-RestMethod -Method Post -Uri "$apiUrl/api/admin/users" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    Write-Host "$($sample.username): created"
  } catch {
    if ($_.ErrorDetails.Message -match 'username already exists') { Write-Host "$($sample.username): already exists" } else { throw }
  }
}
