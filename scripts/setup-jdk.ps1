$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JdkDir = Join-Path $ProjectRoot ".jdk"
$Marker = Join-Path $JdkDir ".version"
$TargetVersion = "17.0.19+10"

if (Test-Path $Marker) {
    $installed = Get-Content $Marker -Raw
    if ($installed.Trim() -eq $TargetVersion) {
        $existing = Get-ChildItem $JdkDir -Directory | Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } | Select-Object -First 1
        if ($existing) {
            Write-Host "JDK already installed: $($existing.FullName)"
            exit 0
        }
    }
}

Write-Host "Downloading Eclipse Temurin JDK $TargetVersion..."
New-Item -ItemType Directory -Force -Path $JdkDir | Out-Null

$ZipPath = Join-Path $JdkDir "temurin17.zip"
Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $ZipPath
Expand-Archive -Path $ZipPath -DestinationPath $JdkDir -Force
Remove-Item $ZipPath

$TargetVersion | Set-Content $Marker
$installedJdk = Get-ChildItem $JdkDir -Directory | Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } | Select-Object -First 1
Write-Host "JDK installed: $($installedJdk.FullName)"
Write-Host "Run: .\gradlew.bat :core:test"
