param(
    [string]$BaseUrl = 'http://127.0.0.1:8082',
    [int]$TimeoutSeconds = 90,
    [switch]$LocalPostgres
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceId = '20000000-0000-0000-0000-000000000001'
$destinationId = '20000000-0000-0000-0000-000000000002'
$psqlPath = 'C:\Program Files\PostgreSQL\15\bin\psql.exe'
$pgPassword = $null
$authHeaders = @{}
if ($LocalPostgres) {
    if (-not (Test-Path $psqlPath)) { throw "psql bulunamadı: $psqlPath" }
    $securePassword = Read-Host 'Yerel PostgreSQL postgres parolası' -AsSecureString
    $pgPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Get-Json([string]$path) {
    return Invoke-RestMethod -Method Get -Uri "$BaseUrl$path" -Headers $authHeaders
}

function Post-Json([string]$path, [object]$payload, [string]$key) {
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl$path" -ContentType 'application/json' `
        -Headers ($authHeaders + @{ 'Idempotency-Key' = $key }) -Body ($payload | ConvertTo-Json -Compress)
}

function Wait-Payment([string]$id, [string]$expected) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $payment = Get-Json "/api/bill-payments/$id"
        if ($payment.status -eq $expected) { return $payment }
        if ($payment.status -in @('SUCCEEDED', 'FAILED') -and $payment.status -ne $expected) {
            throw "Ödeme $id beklenen $expected yerine $($payment.status) oldu"
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Ödeme $id $TimeoutSeconds saniyede $expected durumuna ulaşmadı"
}

function Read-DbScalar([string]$service, [string]$database, [string]$user, [string]$password, [string]$sql) {
    if ($LocalPostgres) {
        if ($database -eq 'corebank') { $database = 'benermcs' }
        $env:PGPASSWORD = $pgPassword
        try {
            $value = & $psqlPath -h 127.0.0.1 -p 5432 -U postgres -d $database -t -A -P pager=off -c $sql
        } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    } else {
        $value = docker compose exec -T -e "PGPASSWORD=$password" $service `
            psql -U $user -d $database -t -A -c $sql
    }
    if ($LASTEXITCODE -ne 0) { throw "$database SQL sorgusu başarısız" }
    return ($value | Out-String).Trim()
}

Push-Location $projectRoot
try {
    docker compose config -q
    if ($LASTEXITCODE -ne 0) { throw 'Compose yapılandırması geçersiz' }
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker motoru çalışmıyor' }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $login = $null
    do {
        try {
            $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' `
                -Body (@{ username = 'bener'; password = 'Bener123!' } | ConvertTo-Json -Compress)
        } catch {
            if ((Get-Date) -ge $deadline) { throw }
            Start-Sleep -Seconds 2
        }
    } while ($null -eq $login)
    Assert-True (-not [string]::IsNullOrWhiteSpace($login.accessToken)) 'JWT erişim anahtarı üretilemedi'
    $authHeaders = @{ Authorization = "Bearer $($login.accessToken)" }

    $beforeSource = Get-Json "/api/accounts/$sourceId"
    $beforeDestination = Read-DbScalar 'postgres' 'corebank' 'corebank' 'corebank_local_only' `
        "SELECT balance FROM accounts WHERE id = '$destinationId'::uuid"
    try {
        $null = Get-Json "/api/accounts/$destinationId"
        throw 'Başka müşterinin hesabı API üzerinden okunabildi'
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        Assert-True ($status -eq 403) 'Hesap sahipliği denetimi 403 döndürmedi'
    }
    Assert-True ([decimal]$beforeSource.availableBalance -ge 4) 'Demo gönderici hesabında en az 4 TRY gerekli'

    $transferKey = "smoke-transfer-$([guid]::NewGuid())"
    $transferPayload = @{ sourceAccountId = $sourceId; destinationAccountId = $destinationId; amount = 1.00 }
    $transfer = Post-Json '/api/transfers' $transferPayload $transferKey
    $repeat = Post-Json '/api/transfers' $transferPayload $transferKey
    Assert-True ($transfer.id -eq $repeat.id) 'Tekrar isteği yeni transfer oluşturdu'

    $afterSource = Get-Json "/api/accounts/$sourceId"
    $afterDestination = Read-DbScalar 'postgres' 'corebank' 'corebank' 'corebank_local_only' `
        "SELECT balance FROM accounts WHERE id = '$destinationId'::uuid"
    Assert-True (([decimal]$beforeSource.balance - [decimal]$afterSource.balance) -eq 1) 'Gönderen bakiyesi beklenen kadar azalmadı'
    Assert-True (([decimal]$afterDestination - [decimal]$beforeDestination) -eq 1) 'Alıcı bakiyesi beklenen kadar artmadı'
    $ledger = @(Get-Json "/api/accounts/$sourceId/ledger")
    Assert-True (@($ledger | Where-Object { $_.transferId -eq $transfer.id -and $_.direction -eq 'DEBIT' }).Count -eq 1) 'DEBIT ledger kaydı bulunamadı'
    $spending = Get-Json '/api/spending'
    Assert-True (@($spending.items | Where-Object { $_.id -eq $transfer.id }).Count -ge 1) 'Harcama geçmişinde transfer bulunamadı'

    $approve = Post-Json '/api/bill-payments' @{ accountId = $sourceId; amount = 1.00; scenario = 'APPROVE' } "smoke-approve-$([guid]::NewGuid())"
    $approve = Wait-Payment $approve.id 'SUCCEEDED'
    $reject = Post-Json '/api/bill-payments' @{ accountId = $sourceId; amount = 1.00; scenario = 'REJECT' } "smoke-reject-$([guid]::NewGuid())"
    $reject = Wait-Payment $reject.id 'FAILED'
    $timeout = Post-Json '/api/bill-payments' @{ accountId = $sourceId; amount = 1.00; scenario = 'TIMEOUT_ONCE' } "smoke-timeout-$([guid]::NewGuid())"
    $timeout = Wait-Payment $timeout.id 'SUCCEEDED'

    $providerResult = Read-DbScalar 'payment-postgres' 'paymentdb' 'payment' 'payment_local_only' `
        "SELECT status FROM provider_payments WHERE id = '$($timeout.id)'::uuid"
    Assert-True ($providerResult -eq 'ACCEPTED') 'Ödeme sağlayıcısında idempotent sonuç bulunamadı'

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $notificationCount = Read-DbScalar 'notification-postgres' 'notificationdb' 'notification' 'notification_local_only' `
            "SELECT count(*) FROM notifications WHERE transfer_id = '$($transfer.id)'::uuid"
        if ($notificationCount -eq '1') { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Assert-True ($notificationCount -eq '1') 'Kafka üzerinden bildirim kaydı oluşmadı'

    $outboxPublished = Read-DbScalar 'postgres' 'corebank' 'corebank' 'corebank_local_only' `
        "SELECT count(*) FROM outbox_events WHERE aggregate_id = '$($transfer.id)'::uuid AND published_at IS NOT NULL"
    Assert-True ($outboxPublished -eq '1') 'Outbox olayı yayımlanmış görünmüyor'

    $null = Get-Json "/api/reports/accounts/$sourceId/transfer-count"
    $redisKeyPresent = docker compose exec -T redis redis-cli EXISTS "transferCounts::$sourceId"
    if ($LASTEXITCODE -ne 0) { throw 'Redis sorgusu başarısız' }
    Assert-True ([int](($redisKeyPresent | Out-String).Trim()) -eq 1) 'Rapor cache kaydı Redis içinde bulunamadı'

    [pscustomobject]@{
        Result = 'PASS'
        TransferId = $transfer.id
        RepeatedTransferId = $repeat.id
        ApprovedPaymentId = $approve.id
        RejectedPaymentId = $reject.id
        TimeoutPaymentId = $timeout.id
        NotificationCount = $notificationCount
        PublishedOutboxCount = $outboxPublished
        RedisReportKeyPresent = ($redisKeyPresent | Out-String).Trim()
        JwtSubject = $login.username
        ForeignAccountAccess = '403 FORBIDDEN'
    } | Format-List
}
finally {
    Remove-Variable pgPassword,securePassword -ErrorAction SilentlyContinue
    Pop-Location
}
