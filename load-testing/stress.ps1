param (
    [ValidateSet("catalog", "ticketing", "both")]
    [string]$Target = "both",

    [int]$Rps = 10,

    [int]$Duration = 60,

    [string]$HostIp = "localhost"
)

$CatalogUrl = "http://${HostIp}:30081"
$TicketingUrl = "http://${HostIp}:30082"

$DelayMs = [Math]::Max(1, [int](1000 / $Rps))

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   CineSystem - Load Testing (PowerShell)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ("  Target:    {0}" -f $Target) -ForegroundColor Cyan
Write-Host ("  RPS:       {0} req/seg" -f $Rps) -ForegroundColor Cyan
Write-Host ("  Duracion:  {0} segundos" -f $Duration) -ForegroundColor Cyan
Write-Host ("  Destino:   {0}" -f $HostIp) -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Iniciando generacion de carga... Presiona Ctrl+C para detener." -ForegroundColor Yellow
Write-Host ""

$TotalRequests = 0
$SuccessCount = 0
$FailCount = 0

$Movies = @("MOV001", "MOV002", "MOV003", "MOV004")
$Showtimes = @("SH001", "SH002", "SH004", "SH006", "SH008")
$Customers = @("Carlos Ruiz", "Maria Lopez", "Andres Silva", "Lucia Gomez", "Pedro Fernandez")

$EndTime = (Get-Date).AddSeconds($Duration)
$StartTime = Get-Date

while ((Get-Date) -lt $EndTime) {
    $LoopStart = Get-Date

    $CurrentTarget = $Target
    if ($Target -eq "both") {
        if ((Get-Random -Minimum 0 -Maximum 10) -lt 6) {
            $CurrentTarget = "catalog"
        } else {
            $CurrentTarget = "ticketing"
        }
    }

    try {
        if ($CurrentTarget -eq "catalog") {
            $resp = Invoke-RestMethod -Uri "$CatalogUrl/api/movies" -Method GET -TimeoutSec 3 -ErrorAction Stop
            $SuccessCount++
        } else {
            $randMovie = $Movies | Get-Random
            $randShowtime = $Showtimes | Get-Random
            $randCust = $Customers | Get-Random
            $email = ($randCust -replace ' ', '.').ToLower() + "@cine.com"
            $body = @{
                movieId = $randMovie
                showtimeId = $randShowtime
                customerName = $randCust
                customerEmail = $email
                quantity = (Get-Random -Minimum 1 -Maximum 4)
            } | ConvertTo-Json

            $resp = Invoke-RestMethod -Uri "$TicketingUrl/api/tickets/purchase" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 3 -ErrorAction Stop
            $SuccessCount++
        }
    } catch {
        $FailCount++
    }

    $TotalRequests++

    if ($TotalRequests % 10 -eq 0) {
        $elapsed = [int]((Get-Date) - $StartTime).TotalSeconds
        $currentRps = [Math]::Round($TotalRequests / [Math]::Max(1, $elapsed), 1)
        Write-Host "  -> [$elapsed s / $Duration s] Enviados: $TotalRequests (Exitosos: $SuccessCount | Fallidos: $FailCount) | RPS: $currentRps" -ForegroundColor DarkGray
    }

    $elapsedLoopMs = ((Get-Date) - $LoopStart).TotalMilliseconds
    $sleepMs = [Math]::Max(0, [int]($DelayMs - $elapsedLoopMs))
    if ($sleepMs -gt 0) {
        Start-Sleep -Milliseconds $sleepMs
    }
}

$TotalTime = [Math]::Round(((Get-Date) - $StartTime).TotalSeconds, 2)
$AvgRps = [Math]::Round($TotalRequests / [Math]::Max(1, $TotalTime), 2)
$SuccessRate = if ($TotalRequests -gt 0) { [Math]::Round(($SuccessCount / $TotalRequests) * 100, 1) } else { 0 }

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "              RESUMEN DE LA PRUEBA" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ("  Peticiones Totales:  {0}" -f $TotalRequests) -ForegroundColor Green
Write-Host ("  Exitosas (2xx):      {0}" -f $SuccessCount) -ForegroundColor Green
Write-Host ("  Fallidas (4xx/5xx):  {0}" -f $FailCount) -ForegroundColor Green
Write-Host ("  Tasa de Exito:       {0} %" -f $SuccessRate) -ForegroundColor Green
Write-Host ("  Tiempo Total:        {0} s" -f $TotalTime) -ForegroundColor Green
Write-Host ("  RPS Promedio:        {0} req/s" -f $AvgRps) -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
