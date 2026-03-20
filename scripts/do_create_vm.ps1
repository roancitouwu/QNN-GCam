# DigitalOcean VM Creation Script (PowerShell)
# Requiere: Token de DigitalOcean

param(
    [Parameter(Mandatory=$true)]
    [string]$DOToken,
    
    [string]$DropletName = "qnn-gcam-build",
    [string]$Region = "nyc1",
    [string]$Size = "c-8",  # CPU-Optimized 8 vCPUs, 16GB RAM
    [string]$Image = "ubuntu-24-04-x64"
)

$headers = @{
    "Authorization" = "Bearer $DOToken"
    "Content-Type" = "application/json"
}

Write-Host "=== QNN-GCam Build Server Setup ===" -ForegroundColor Cyan

# 1. Get SSH Keys
Write-Host "[1/4] Obteniendo SSH keys..." -ForegroundColor Yellow
$sshKeysResponse = Invoke-RestMethod -Uri "https://api.digitalocean.com/v2/account/keys" -Headers $headers -Method Get
$sshKeyId = $sshKeysResponse.ssh_keys[0].id

if (-not $sshKeyId) {
    Write-Host "ERROR: No se encontraron SSH keys en tu cuenta de DigitalOcean" -ForegroundColor Red
    Write-Host "Primero agrega una SSH key en: https://cloud.digitalocean.com/account/security" -ForegroundColor Yellow
    exit 1
}

Write-Host "  SSH Key ID: $sshKeyId" -ForegroundColor Green

# 2. Create Droplet
Write-Host "[2/4] Creando droplet..." -ForegroundColor Yellow

$body = @{
    name = $DropletName
    region = $Region
    size = $Size
    image = $Image
    ssh_keys = @($sshKeyId)
    backups = $false
    ipv6 = $false
    monitoring = $true
    tags = @("qnn-gcam", "build-server")
} | ConvertTo-Json

$createResponse = Invoke-RestMethod -Uri "https://api.digitalocean.com/v2/droplets" -Headers $headers -Method Post -Body $body
$dropletId = $createResponse.droplet.id

Write-Host "  Droplet ID: $dropletId" -ForegroundColor Green

# 3. Wait for droplet to be ready
Write-Host "[3/4] Esperando que el droplet esté listo..." -ForegroundColor Yellow
$maxAttempts = 60
$attempt = 0

do {
    Start-Sleep -Seconds 5
    $attempt++
    $statusResponse = Invoke-RestMethod -Uri "https://api.digitalocean.com/v2/droplets/$dropletId" -Headers $headers -Method Get
    $status = $statusResponse.droplet.status
    Write-Host "  Estado: $status (intento $attempt/$maxAttempts)" -ForegroundColor Gray
} while ($status -ne "active" -and $attempt -lt $maxAttempts)

if ($status -ne "active") {
    Write-Host "ERROR: Timeout esperando droplet" -ForegroundColor Red
    exit 1
}

# 4. Get IP
$dropletIp = ($statusResponse.droplet.networks.v4 | Where-Object { $_.type -eq "public" }).ip_address

Write-Host ""
Write-Host "=== DROPLET CREADO ===" -ForegroundColor Green
Write-Host "ID: $dropletId" -ForegroundColor Cyan
Write-Host "IP: $dropletIp" -ForegroundColor Cyan
Write-Host "SSH: ssh root@$dropletIp" -ForegroundColor Cyan
Write-Host ""
Write-Host "Siguiente paso:" -ForegroundColor Yellow
Write-Host "  1. Espera ~30 segundos para que SSH esté disponible"
Write-Host "  2. ssh root@$dropletIp"
Write-Host "  3. Copia y ejecuta vm_init.sh"
Write-Host ""

# Save info to file
$info = @{
    droplet_id = $dropletId
    ip = $dropletIp
    name = $DropletName
    created = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
} | ConvertTo-Json

$info | Out-File -FilePath "droplet_info.json" -Encoding utf8
Write-Host "Info guardada en: droplet_info.json" -ForegroundColor Gray
