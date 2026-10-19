[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$MapZipPath,

    [string]$DestinationPath = (Join-Path $PSScriptRoot "..\map-assets")
)

$ErrorActionPreference = "Stop"

$zipItem = Get-Item -LiteralPath $MapZipPath
if (-not $zipItem -or $zipItem.PSIsContainer) {
    throw "Map ZIP file was not found: $MapZipPath"
}

$destination = [System.IO.Path]::GetFullPath($DestinationPath)
$tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("dpwfms-map-" + [guid]::NewGuid().ToString("N"))

try {
    New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null
    Expand-Archive -LiteralPath $zipItem.FullName -DestinationPath $tempDirectory -Force

    $archive = Get-ChildItem -LiteralPath $tempDirectory -Filter "dubai_area.pmtiles" -File -Recurse |
        Select-Object -First 1
    if (-not $archive) {
        throw "dubai_area.pmtiles was not found inside $($zipItem.Name)."
    }

    $mapDirectory = $archive.Directory.FullName
    $stylePath = Join-Path $mapDirectory "style.json"
    if (-not (Test-Path -LiteralPath $stylePath -PathType Leaf)) {
        $style = Get-ChildItem -LiteralPath $tempDirectory -Filter "style.json" -File -Recurse |
            Select-Object -First 1
        if (-not $style) {
            throw "style.json was not found next to the PMTiles archive or elsewhere in the ZIP."
        }
        $stylePath = $style.FullName
    }

    $fontsPath = Join-Path $mapDirectory "fonts"
    if (-not (Test-Path -LiteralPath $fontsPath -PathType Container)) {
        $fonts = Get-ChildItem -LiteralPath $tempDirectory -Directory -Recurse |
            Where-Object { $_.Name -eq "fonts" } |
            Select-Object -First 1
        if ($fonts) {
            $fontsPath = $fonts.FullName
        } else {
            throw "The fonts directory was not found inside $($zipItem.Name)."
        }
    }

    $styleText = [System.IO.File]::ReadAllText($stylePath)
    $styleText = $styleText.Replace("/map/fonts/", "/api/maps/fonts/")
    $null = $styleText | ConvertFrom-Json -ErrorAction Stop

    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath $archive.FullName -Destination (Join-Path $destination "dubai_area.pmtiles") -Force
    [System.IO.File]::WriteAllText(
        (Join-Path $destination "style.json"),
        $styleText,
        [System.Text.UTF8Encoding]::new($false)
    )

    $destinationFonts = Join-Path $destination "fonts"
    if (Test-Path -LiteralPath $destinationFonts) {
        Remove-Item -LiteralPath $destinationFonts -Recurse -Force
    }
    New-Item -ItemType Directory -Path $destinationFonts -Force | Out-Null
    Copy-Item -Path (Join-Path $fontsPath "*") -Destination $destinationFonts -Recurse -Force

    $env:MAP_ASSETS_DIRECTORY = $destination

    $sizeMb = [math]::Round((Get-Item -LiteralPath (Join-Path $destination "dubai_area.pmtiles")).Length / 1MB, 1)
    Write-Host "PMTiles map assets installed in: $destination"
    Write-Host "Archive size: $sizeMb MB"
    Write-Host "Java map style URL: http://localhost:8080/api/maps/style.json"
    Write-Host "MAP_ASSETS_DIRECTORY is set for this PowerShell session."
    Write-Host ""
    Write-Host "Next, build and run the API from the repository root:"
    Write-Host "  .\mvnw.cmd -pl fms-api -am clean package -DskipTests"
    Write-Host "  java -jar .\fms-api\target\fms-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev,eclipse --spring.config.import=optional:file:.env[.properties]"
    Write-Host ""
    Write-Host "The API needs local PostgreSQL, Redis, and RabbitMQ services running."
    Write-Host "Start the frontend separately with: cd fms-web; npm ci; npm run dev"
}
finally {
    if (Test-Path -LiteralPath $tempDirectory) {
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force
    }
}
