$base = "http://localhost:8080"

function Test-Endpoint {
    param([string]$Method, [string]$Url, [hashtable]$Headers = @{}, [object]$Body = $null)
    $params = @{ Uri = "$base$Url"; Method = $Method; Headers = $Headers; UseBasicParsing = $true; ErrorAction = "Stop" }
    if ($Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $params.ContentType = "application/json"
    }
    try {
        $r = Invoke-WebRequest @params
        $code = [int]$r.StatusCode
        $content = if ($r.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $content = $reader.ReadToEnd()
            } catch { $content = "" }
        } else {
            $code = 0
            $content = $_.Exception.Message
        }
    }
    $clean = ([string]$content -replace '\s+', ' ').Trim()
    $snippet = if ($clean.Length -gt 105) { $clean.Substring(0, 105) + "..." } else { $clean }
    $color = if ($code -ge 200 -and $code -lt 300) { "Green" } elseif ($code -ge 400 -and $code -lt 500) { "Yellow" } else { "Red" }
    Write-Host ("[{0}] {1,-6} {2,-58}" -f $code, $Method, $Url) -ForegroundColor $color -NoNewline
    Write-Host " $snippet"
    return @{ Code = $code; Content = $content }
}

Write-Host "`n===== 1. ENDPOINTS PUBLICOS =====" -ForegroundColor Cyan
Test-Endpoint "GET" "/v3/api-docs"              | Out-Null
Test-Endpoint "GET" "/api/roles"                | Out-Null
Test-Endpoint "GET" "/api/appointment-statuses" | Out-Null

Write-Host "`n===== 2. LOGIN (1 membership -> tenant token directo) =====" -ForegroundColor Cyan
$login = Test-Endpoint "POST" "/api/auth/token" @{} @{ email = "admin@optima.com"; password = "12345678" }
$loginJson = $login.Content | ConvertFrom-Json
$adminTok = $loginJson.token
$auth = @{ Authorization = "Bearer $adminTok" }
Write-Host "  -> tokenType=$($loginJson.tokenType)  token len=$($adminTok.Length)"

Write-Host "`n===== 3. /api/me/* =====" -ForegroundColor Cyan
Test-Endpoint "GET" "/api/me"             $auth | Out-Null
Test-Endpoint "GET" "/api/me/businesses"  $auth | Out-Null

Write-Host "`n===== 4. CRUD del propio negocio (READ) =====" -ForegroundColor Cyan
Test-Endpoint "GET" "/api/businesses/1"                                  $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/hours"                            $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/taxes?page=0&size=5"              $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/categories?page=0&size=5"         $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/services?page=0&size=5"           $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/clients?page=0&size=5"            $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/users?page=0&size=5"              $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/booths?page=0&size=5"             $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/schedule-blocks?page=0&size=5"    $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/appointments?page=0&size=10"      $auth | Out-Null

Write-Host "`n===== 5. AVAILABILITY =====" -ForegroundColor Cyan
Test-Endpoint "GET" "/api/businesses/1/availability?date=2027-03-17&serviceIds=1" $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/availability?date=2027-03-21&serviceIds=1" $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/availability?date=2027-05-15&serviceIds=1" $auth | Out-Null
Test-Endpoint "GET" "/api/businesses/1/availability?date=2027-03-17"              $auth | Out-Null

Write-Host "`n===== 6. MUTACIONES + updated_at (v19) =====" -ForegroundColor Cyan
$a = Test-Endpoint "GET" "/api/businesses/1/appointments/1" $auth
$before = ($a.Content | ConvertFrom-Json).updatedAt
Write-Host "  updatedAt antes:   $before"
Test-Endpoint "PATCH" "/api/businesses/1/appointments/1/payment" $auth @{ isPaid = $true } | Out-Null
$b = Test-Endpoint "GET" "/api/businesses/1/appointments/1" $auth
$after = ($b.Content | ConvertFrom-Json).updatedAt
Write-Host "  updatedAt despues: $after"
Test-Endpoint "PATCH" "/api/businesses/1/appointments/1/status" $auth @{ statusName = "CONFIRMED" } | Out-Null

Write-Host "`n===== 7. SCHEDULE_BLOCK chk_block_target (v19) =====" -ForegroundColor Cyan
Write-Host "Tipo 4 (membership + booth juntos) -> debe 400" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/schedule-blocks" $auth @{ membershipId = 3; boothId = 1; startDate = "2027-09-01"; endDate = "2027-09-01"; reason = "Imposible" } | Out-Null
Write-Host "Bloqueo por empleado -> 201" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/schedule-blocks" $auth @{ membershipId = 3; startDate = "2027-08-01"; endDate = "2027-08-15"; reason = "Vacaciones Maria" } | Out-Null
Write-Host "Bloqueo por cabina -> 201" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/schedule-blocks" $auth @{ boothId = 1; startDate = "2027-09-10"; endDate = "2027-09-10"; reason = "Mantenimiento Sala 1" } | Out-Null
Write-Host "Bloqueo global -> 201" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/schedule-blocks" $auth @{ startDate = "2027-12-25"; endDate = "2027-12-25"; reason = "Navidad" } | Out-Null

Write-Host "`n===== 8. CROSS-TENANT (admin de b1 -> 403 en b2) =====" -ForegroundColor Cyan
Test-Endpoint "GET" "/api/businesses/2/clients" $auth | Out-Null

Write-Host "`n===== 9. CREAR CITA con cabina =====" -ForegroundColor Cyan
Test-Endpoint "POST" "/api/businesses/1/appointments" $auth @{
    clientId = 2; membershipId = 4; serviceIds = @(1); boothId = 2
    startDateTime = "2027-04-15T10:00:00"; notes = "Smoke test"
} | Out-Null

Write-Host "`n===== 10. REGISTRO publico =====" -ForegroundColor Cyan
$ts = [int][double]::Parse((Get-Date -UFormat %s))
$reg = Test-Endpoint "POST" "/api/auth/register" @{} @{
    business = @{ name = "Smoke $ts"; slug = "smoke-$ts"; email = "smoke$ts@test.com"; appointmentInterval = 30 }
    admin    = @{ fullName = "Admin Smoke $ts"; email = "admin.smoke$ts@test.com"; password = "12345678"; phone = "600000000" }
}
$regJson = $reg.Content | ConvertFrom-Json
Write-Host "  -> tokenType=$($regJson.tokenType)  token len=$($regJson.token.Length)"

Write-Host "`n===== 11. FORGOT PASSWORD (publico, siempre 204) =====" -ForegroundColor Cyan
Test-Endpoint "POST" "/api/auth/forgot-password" @{} @{ email = "admin@optima.com" } | Out-Null
Test-Endpoint "POST" "/api/auth/forgot-password" @{} @{ email = "noexiste@x.y" }      | Out-Null

Write-Host "`n===== 12. ERRORES ESPERADOS =====" -ForegroundColor Cyan
Write-Host "401 sin token:" -ForegroundColor DarkGray
Test-Endpoint "GET" "/api/businesses/1/clients" | Out-Null
Write-Host "400 body invalido:" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/taxes" $auth @{ name = ""; percentage = "abc" } | Out-Null
Write-Host "404 cita inexistente:" -ForegroundColor DarkGray
Test-Endpoint "GET" "/api/businesses/1/appointments/999" $auth | Out-Null
Write-Host "405 metodo no soportado:" -ForegroundColor DarkGray
Test-Endpoint "DELETE" "/api/roles" | Out-Null
Write-Host "409 conflicto al crear cita en bloqueo San Isidro:" -ForegroundColor DarkGray
Test-Endpoint "POST" "/api/businesses/1/appointments" $auth @{
    clientId = 2; membershipId = 3; serviceIds = @(1)
    startDateTime = "2027-05-15T10:00:00"
} | Out-Null

Write-Host "`n===== FIN =====" -ForegroundColor Cyan
