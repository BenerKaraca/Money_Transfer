$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaPath = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$npmPath = (Get-Command npm.cmd -ErrorAction Stop).Source
$logDir = Join-Path $projectRoot '.runlogs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

if (-not (Test-Path $javaPath)) { throw "Java bulunamadı: $javaPath" }

function Wait-Http([string]$url, [string]$name, [int]$seconds = 90) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $url -TimeoutSec 3 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
        } catch { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $deadline)
    throw "$name hazır olmadı. Günlük: $logDir"
}

function Start-JavaApp([string]$name, [string]$jarPath, [hashtable]$environment) {
    $jar = (Resolve-Path (Join-Path $projectRoot $jarPath)).Path
    $process = Start-Process -FilePath $javaPath -ArgumentList @('-jar', $jar) `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -Environment $environment -PassThru `
        -RedirectStandardOutput (Join-Path $logDir "$name.out.log") `
        -RedirectStandardError (Join-Path $logDir "$name.err.log")
    return $process.Id
}

Push-Location $projectRoot
try {
    docker compose up -d broker redis
    if ($LASTEXITCODE -ne 0) { throw 'Kafka veya Redis başlatılamadı' }

    $deadline = (Get-Date).AddSeconds(90)
    do {
        $kafkaStatus = docker compose exec -T broker /opt/kafka/bin/kafka-topics.sh `
            --bootstrap-server broker:19092 --list 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Kafka broker hazır olmadı' }
    $redisStatus = docker compose exec -T redis redis-cli ping
    if ($LASTEXITCODE -ne 0 -or (($redisStatus | Out-String).Trim() -ne 'PONG')) { throw 'Redis hazır değil' }

    $securePassword = Read-Host 'Yerel PostgreSQL postgres parolası' -AsSecureString
    $pgPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
    if ([string]::IsNullOrEmpty($pgPassword)) { throw 'PostgreSQL parolası boş olamaz' }

    $pids = [ordered]@{}
    $pids.provider = Start-JavaApp 'payment-provider' 'services/payment-provider/target/payment-provider-0.1.0-SNAPSHOT.jar' @{
        PAYMENT_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/paymentdb'
        PAYMENT_DB_USER = 'postgres'
        PAYMENT_DB_PASSWORD = $pgPassword
    }
    Wait-Http 'http://127.0.0.1:8081/provider/payments/00000000-0000-0000-0000-000000000000' 'Payment Provider'

    $pids.corebank = Start-JavaApp 'corebank' 'target/corebank-0.1.0-SNAPSHOT.jar' @{
        DB_URL = 'jdbc:postgresql://127.0.0.1:5432/benermcs'
        DB_USER = 'postgres'
        DB_PASSWORD = $pgPassword
        KAFKA_BOOTSTRAP = '127.0.0.1:9092'
        REDIS_HOST = '127.0.0.1'
        MESSAGING_ENABLED = 'true'
        PAYMENTS_ENABLED = 'true'
        PAYMENT_PROVIDER_URL = 'http://127.0.0.1:8081'
        SPRING_PROFILES_ACTIVE = 'demo,redis'
    }
    Wait-Http 'http://127.0.0.1:8080/actuator/health' 'CoreBank'

    $pids.notification = Start-JavaApp 'notification' 'services/notification/target/notification-service-0.1.0-SNAPSHOT.jar' @{
        NOTIFICATION_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/notificationdb'
        NOTIFICATION_DB_USER = 'postgres'
        NOTIFICATION_DB_PASSWORD = $pgPassword
        KAFKA_BOOTSTRAP = '127.0.0.1:9092'
    }
    $pids.gateway = Start-JavaApp 'gateway' 'services/gateway/target/corebank-gateway-0.1.0-SNAPSHOT.jar' @{
        COREBANK_URL = 'http://127.0.0.1:8080'
    }
    Wait-Http 'http://127.0.0.1:8082/actuator/health' 'Gateway'

    $frontend = Start-Process -FilePath $npmPath -ArgumentList @('run','dev','--','--port','3000') `
        -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'frontend.out.log') `
        -RedirectStandardError (Join-Path $logDir 'frontend.err.log')
    $pids.frontend = $frontend.Id
    Wait-Http 'http://127.0.0.1:3000/' 'Frontend'

    $pids | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logDir 'pids.json')
    Write-Host 'CoreBank, Payment Provider, Notification, Gateway, Frontend, Kafka ve Redis başlatıldı.'
    Write-Host 'Arayüz: http://127.0.0.1:3000'
    Write-Host "Günlükler: $logDir"
}
finally {
    Remove-Variable pgPassword,securePassword -ErrorAction SilentlyContinue
    Pop-Location
}
