# 04 — Dağıtık mimari, CAP, dayanıklılık ve kalite

## Monolitten servislere

CoreBank çekirdeği hesap, transfer, ledger, fatura rezervasyonu ve Outbox'ı tek veritabanında tutar. Bu sınır içindeki banka içi transfer **tek ACID transaction** kullanır. Ayrı ödeme sağlayıcısı kendi veritabanına sahiptir; CoreBank onu HTTP üzerinden çağırır. Bildirim servisi ayrı veritabanında Kafka olaylarını tüketir. Gateway, Spring Cloud ile `/api/**` isteklerini CoreBank'e yönlendirir.

İlk monolitin avantajı tek transaction, kolay yerel hata ayıklama ve düşük işletim yüküdür. Ayrı servislerin avantajı bağımsız geliştirme/ölçekleme ve veri sahipliğidir. Bedeli ağ gecikmesi, kısmi hata, observability ve dağıtık tutarlılıktır. Her servis için ayrı veritabanı konteyneri tanımlanır. Ödeme ve bildirim servisleri CoreBank tablolarına doğrudan erişmez.

## SAGA

`BillPaymentService.start` hesabın kullanılabilir bakiyesinden tutarı rezerve eder ve `PENDING` yazar. `PaymentSagaOrchestrator`, ödeme sağlayıcısına aynı `paymentId` ile idempotent istek gönderir. Sonuç:

| Sağlayıcı sonucu | CoreBank işlemi |
| --- | --- |
| `ACCEPTED` | Rezervasyon çözülür; clearing hesabına transfer ve iki ledger satırı tek yerel transaction'da yazılır; `SUCCEEDED`. |
| `REJECTED` | Rezervasyon geri alınır; `FAILED`. Bu telafi işlemidir. |
| Timeout / Circuit Breaker açık | Rezervasyon korunur; `UNKNOWN`. Aynı kimlikle tekrar sorgulanır/gönderilir. Başarı uydurulmaz. |

Ödeme sağlayıcısı `TIMEOUT_ONCE` senaryosunda sonucu **kendi veritabanına commit ettikten sonra** yanıtı geciktirir. Böylece CoreBank'in sonucu bilmediği ama sağlayıcının kabul ettiği gerçekçi durum test edilir. `BillPaymentSagaTest` üç yolu doğrular.

## Outbox + Inbox

Transfer, ledger ve `outbox_events` aynı transaction'da yazılır. `OutboxScheduler`, henüz yayımlanmamış olayları Kafka'ya gönderir; başarılı ack sonrası `published_at` yazar. Ack sonrası DB commit öncesi çökme olursa olay tekrar gidebilir. Bildirim servisindeki `inbox_events.event_id` unique kısıtı ve aynı transaction'daki bildirim yazımı tekrarı zararsız kılar. Bu **en az bir kez teslimat + idempotent işleme** modelidir; uçtan uca exactly-once iddiası değildir.

## CAP ve Circuit Breaker

Ağ bölünmesinde CoreBank sağlayıcının sonucunu kesin olarak bilemez. Finansal sonuç için doğruluk tercih edilir: istek `UNKNOWN` kalır, rezervasyon tutulur ve mutabakat yapılır. Kullanılabilirliği artırmak amacıyla istekleri başarılı göstermiyoruz. Circuit Breaker başarısız çağrıları sınırlayıp hızlı dönüş sağlar; HTTP istemcisinde ayrıca bağlantı ve okuma timeout'ları vardır.

Ölçekleme: Gateway ve servisler birden çok replica ile çalışabilir. CoreBank Outbox yayıncıları eşzamanlı aynı olayı gönderebilir; Inbox bunu idempotent işler. Daha yüksek yükte Outbox seçimine `SKIP LOCKED`/claim mekanizması, partition tasarımı ve ölçüm eklemek gerekir. Kafka anahtarında transfer kimliği kullanılır; aynı aggregate olayları aynı partition'a gider. Redis sadece 30 saniyelik transfer sayısı raporunu cache'ler; bakiye kaynağı PostgreSQL'dir.

## SonarQube

Önce tüm Maven modüllerini derle ve `frontend` için `npm run build` çalıştır. Sonra `docker compose --profile quality up -d sonarqube` ve kendi SonarQube token'ınla `sonar-scanner` çalıştır. Kök `sonar-project.properties` tüm Java/TypeScript kaynaklarını ve testlerini tanımlar. Token'ı repoya yazma; `SONAR_TOKEN` ortam değişkeninden ver. Statik analiz bir kalite sinyalidir; transaction ve hata senaryosu testlerinin yerine geçmez.

## Güvenlik sınırı

CoreBank, Spring Security Resource Server olarak her finansal komutta HS256 JWT doğrular. Login parolası BCrypt ile karşılaştırılır; token içindeki `customer_id` hesap sahipliği denetiminde kullanılır. Piyasa kuru/teklif uçları finansal veri değiştirmediği için FX servisinde herkese açıktır; bakiye değiştiren `/api/exchanges` CoreBank tarafından korunur. Compose sırrı yalnızca yerel demo içindir. Üretimde OIDC sağlayıcısı, asimetrik anahtar rotasyonu, merkezi secret manager ve servisler arası mTLS gerekir.

## Açık geliştirme sınırları

Tam mevduat/fonlama muhasebesi henüz yok. Yerel PostgreSQL ile karma kurulumda ve tümüyle Docker Compose içinde smoke testi geçti; gerçek PostgreSQL eşzamanlılık, ağ arızası ve tekrar teslim davranışı ayrıca sınanmalıdır.
