# ============================================================
# smoke_test_race.ps1 - Re-test empirico del race condition D.2
# ------------------------------------------------------------
# Lanza N peticiones POST /appointments SIMULTANEAS sobre el
# mismo (empleado, slot). Con el fix del commit 95e3958, el
# UNIQUE uq_appointment_active_slot debe dejar pasar exactamente
# 1 cita (201) y rechazar el resto (409).
#
#   ANTES del fix : 3x201  -> 3 citas duplicadas -> race ABIERTA
#   CON el fix    : 1x201 + 9x409                -> race CERRADA
#
# Requisito: la API arrancada (docker compose up -d).
# NO necesita cliente MySQL: el veredicto sale de los codigos HTTP.
#
# Cada ejecucion consume 1 slot. Se puede re-ejecutar varias veces
# (elige el siguiente slot libre). Para partir de BD limpia:
#   docker compose down -v ; docker compose up -d
# ============================================================

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$N    = 10                       # peticiones concurrentes

Write-Host "== Re-test empirico race condition D.2 ==" -ForegroundColor Cyan

# --- 1. Esperar a que la API responda --------------------------------
Write-Host "1) Esperando a la API en $base ..." -NoNewline
$up = $false
for ($i = 0; $i -lt 60 -and -not $up; $i++) {
    try {
        Invoke-WebRequest "$base/v3/api-docs" -UseBasicParsing -TimeoutSec 3 | Out-Null
        $up = $true
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $up) {
    Write-Host " NO responde." -ForegroundColor Red
    Write-Host "   Arranca primero:  docker compose up -d" -ForegroundColor Yellow
    exit 1
}
Write-Host " OK"

# --- 2. Login (admin = 1 membership -> tenant token de business 1) ---
$login = Invoke-RestMethod "$base/api/auth/token" -Method Post -ContentType 'application/json' `
    -Body '{"email":"admin@optima.com","password":"12345678"}'
$token = $login.token
$hdr   = @{ Authorization = "Bearer $token" }
Write-Host "2) Login OK (tokenType=$($login.tokenType))"

# --- 3. Descubrir clientId y serviceId reales del seed ---------------
$clientId  = (Invoke-RestMethod "$base/api/businesses/1/clients"  -Headers $hdr).content[0].id
$serviceId = (Invoke-RestMethod "$base/api/businesses/1/services" -Headers $hdr).content[0].id
Write-Host "3) clientId=$clientId  serviceId=$serviceId"

# --- 4. Buscar un slot libre real via GET /availability -------------
#     Usar /availability garantiza que el slot pasa las 16
#     validaciones (horario, intervalo, bloqueos, etc.).
$slot = $null; $slotDate = $null
for ($d = 30; $d -lt 120 -and -not $slot; $d++) {
    $date = (Get-Date).AddDays($d).ToString('yyyy-MM-dd')
    $av = Invoke-RestMethod "$base/api/businesses/1/availability?date=$date&serviceIds=$serviceId" -Headers $hdr
    if ($av.slots.Count -gt 0) { $slot = $av.slots[0]; $slotDate = $date }
}
if (-not $slot) {
    Write-Host "No se encontro ningun slot libre en 90 dias." -ForegroundColor Red
    exit 1
}
$startDt      = "{0}T{1}" -f $slotDate, $slot.startTime
$membershipId = $slot.membershipId
Write-Host "4) Slot elegido: empleado(membershipId)=$membershipId  inicio=$startDt"

# --- 5. Body identico para las N peticiones --------------------------
#     Sin boothId: probamos el UNIQUE de empleado (uq_appointment_active_slot,
#     hallazgos D.2.001 / D.2.005, el caso principal).
$bodyJson = '{"clientId":' + $clientId + ',"membershipId":' + $membershipId +
            ',"serviceIds":[' + $serviceId + '],"startDateTime":"' + $startDt + '"}'

# --- 6. Disparo SINCRONIZADO de N peticiones concurrentes -----------
#     Cada job hace busy-wait hasta un instante comun y entonces lanza
#     el POST: las N transacciones colisionan de verdad en el servidor.
$apptUrl = "$base/api/businesses/1/appointments"
$fireAt  = [DateTime]::UtcNow.AddSeconds(4).Ticks

$work = {
    param($url, $tok, $body, $fire)
    while ([DateTime]::UtcNow.Ticks -lt $fire) { }   # espera al disparo comun
    try {
        $r = Invoke-WebRequest -Uri $url -Method Post -Body $body `
             -ContentType 'application/json' `
             -Headers @{ Authorization = "Bearer $tok" } -UseBasicParsing
        [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
    }
}

Write-Host "5) Lanzando $N POST simultaneos ..." -NoNewline
$jobs  = 1..$N | ForEach-Object {
    Start-Job -ScriptBlock $work -ArgumentList $apptUrl, $token, $bodyJson, $fireAt
}
$codes = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
Write-Host " hecho"

# --- 7. Veredicto ----------------------------------------------------
$ok  = @($codes | Where-Object { $_ -eq 201 }).Count
$dup = @($codes | Where-Object { $_ -eq 409 }).Count
$bad = @($codes | Where-Object { $_ -ne 201 -and $_ -ne 409 }).Count

Write-Host ""
Write-Host "Codigos HTTP recibidos: $(($codes | Sort-Object) -join ', ')"
Write-Host "  201 Created  : $ok"
Write-Host "  409 Conflict : $dup"
if ($bad -gt 0) { Write-Host "  otros        : $bad  (re-ejecuta el test)" -ForegroundColor Yellow }
Write-Host ""

if ($ok -eq 1) {
    Write-Host "RESULTADO: PASS - race condition CERRADA." -ForegroundColor Green
    Write-Host "  Exactamente 1 cita creada de $N intentos simultaneos; $dup rechazadas con 409." -ForegroundColor Green
} elseif ($ok -gt 1) {
    Write-Host "RESULTADO: FAIL - se crearon $ok citas duplicadas. Race condition ABIERTA." -ForegroundColor Red
} else {
    Write-Host "RESULTADO: REVISAR - 0 citas creadas; algo fallo antes del test (ver codigos arriba)." -ForegroundColor Yellow
}
