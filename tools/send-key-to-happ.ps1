# Отправляет ключ VPN в Happ на телевизоре через его функцию Web Import.
# 1) На ТВ откройте Happ -> Web Import: появится 5-значный код.
# 2) Запустите:  .\send-key-to-happ.ps1 -Code 12345
# Файл key.txt (ваша ссылка vless://...) лежит только у вас, в репозиторий он не попадает.
param(
    [Parameter(Mandatory = $true)][string]$Code,
    [string]$KeyFile = "$PSScriptRoot\key.txt"
)
$key = (Get-Content -Raw -Encoding UTF8 $KeyFile).Trim()
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($key))
$body = @{ data = $b64 } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri "https://check.happ.su/sendtv/$Code" -ContentType "application/json" -Body $body
Write-Host "Отправлено. Проверьте список конфигураций в Happ на телевизоре."
