# Script d'installation
$pluginFile = "target\sweetenergy3d.sh3p"
$destDir = "..\SweetHome3D-7.5-portable\data\plugins"

if (-not (Test-Path $pluginFile)) {
    Write-Host "ERREUR: Fichier non trouve: $pluginFile" -ForegroundColor Red
    Write-Host "Executez d'abord: mvn package" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
}

$destFile = Join-Path $destDir "sweetenergy3d.sh3p"
Copy-Item -Path $pluginFile -Destination $destFile -Force

Write-Host "Plugin installe: $destFile" -ForegroundColor Green
Write-Host "Redemarrez Sweet Home 3D pour voir l'action dans le menu Tools (Outils)" -ForegroundColor Yellow
