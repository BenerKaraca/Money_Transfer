# Test kapsamı ve çalıştırma durumu

Son ölçüm: 30 Ekim 2026. Her Java Maven projesinde JaCoCo 0.8.14 ile `mvn verify`, React tarafında Vitest/V8 ile `npm run test:coverage` çalıştırıldı. **CoreBank'te 40 Java ve React'te 7 test geçti; failure/error yok.** Diğer dört Java servisinin Docker imajı da kendi `mvn verify` kapısından geçti. Maven ve Vitest %100 eşiklerinin altına düşerse komut başarısız olur. Hiçbir üretim sınıfı kapsam hesabından çıkarılmadı.

| Uygulama | Test | Satır kapsamı | Dal kapsamı | Rapor |
| --- | ---: | ---: | ---: | --- |
| CoreBank | 40 | 561 / 561 = **%100** | 169 / 169 = **%100** | `target/site/jacoco/index.html` |
| Payment Provider | 4 | 35 / 35 = **%100** | 20 / 20 = **%100** | `services/payment-provider/target/site/jacoco/index.html` |
| Notification | 3 | 17 / 17 = **%100** | 6 / 6 = **%100** | `services/notification/target/site/jacoco/index.html` |
| Gateway | 2 | 2 / 2 = **%100** | Dal sayacı yok | `services/gateway/target/site/jacoco/index.html` |
| FX Rates | 5 | **%100 kalite kapısı geçti** | **%100 kalite kapısı geçti** | `services/fx-rates/target/site/jacoco/index.html` |
| React | 7 | 152 / 152 = **%100** | 113 / 113 = **%100** | `frontend/coverage/index.html` |

React ölçümünde ayrıca **229/229 ifade** ve **109/109 fonksiyon** kapsandı; `npm run build` geçti. CoreBank entegrasyon testleri H2 üzerinde transfer, rollback, idempotency ve iki thread yarışını; SAGA testleri kabul, ret ve belirsiz sonucu; Hibernate testi N+1 sorgu sayısını doğruluyor. Yeni güvenlik testleri BCrypt, JWT claim'leri, yanlış parola, hesap sahipliği ve 403 yollarını; harcama testi gerçek SQL mapper ve toplamları doğruluyor. %100 ölçümü, ağ kesintisi veya gerçek PostgreSQL satır kilidi yarışının tamamen sınandığı anlamına gelmez.

## Gerçek ortam sonucu

Önce kullanıcının yerel PostgreSQL 15 sunucusunda `benermcs`, `paymentdb` ve `notificationdb` ile karma yerel/Docker kurulumunda smoke testi geçti. Daha sonra **tüm servisler Docker Compose** ile yeniden derlenip başlatıldı. Bu kurulum ayrı PostgreSQL 17 konteynerleri kullanır; CoreBank DB'si hostta `127.0.0.1:5435`, diğerleri `5433` ve `5434` portlarında. Yerel PostgreSQL 15 verileriyle karışmaz. Docker imaj derlemeleri Java `mvn verify` ve React `npm run test:coverage` çalıştırır. Veritabanı parolası kaynak dosyalarına yazılmadı.

`scripts/smoke-test.ps1` Docker Compose ortamında **PASS** döndü. Aşağıdakiler gerçek HTTP, PostgreSQL, Kafka ve Redis üzerinde doğrulandı:

- Gateway üzerinden transfer ve aynı `Idempotency-Key` ile tekrar: tek transfer kimliği, tek borç/alacak etkisi ve `DEBIT` ledger kaydı.
- BCrypt login sonrası JWT üretimi; token ile kullanıcının kendi hesabını okuma ve başka müşterinin hesabına erişimde `403 FORBIDDEN`.
- Yapılan transferin birleşik harcama geçmişinde işlem UUID'siyle bulunması.
- `APPROVE` → `SUCCEEDED`, `REJECT` → `FAILED`, `TIMEOUT_ONCE` → yeniden deneme sonrası `SUCCEEDED`; sağlayıcı veritabanında kabul edilmiş sonuç.
- Transfer için bildirim veritabanında bir Kafka tüketim kaydı; CoreBank veritabanında yayımlanmış Outbox olayı.
- Rapor çağrısı sonrası Redis anahtarının oluşması.
- Frontend HTTP 200 yanıtı ve Gateway health `UP` durumu.

Bu smoke testi ayrı iki isteğin gerçek PostgreSQL satır kilidi altında yarışmasını, Kafka'nın tekrar teslimini veya birden çok Outbox publisher örneğini sınamaz. Bu davranışlar için aşağıdaki ek testler gerekir.

## Yeniden çalıştırma

Tamamen Docker Compose ile:

```powershell
docker compose up --build -d
& .\scripts\smoke-test.ps1
```

Yerel PostgreSQL 15 kullanmak istersen `scripts/start-local-stack.ps1` ve ardından `scripts/smoke-test.ps1 -LocalPostgres` çalıştır; bu yol parolayı etkileşimli ister. Her iki smoke test demo verisine küçük tutarlı işlemler yazar; bakiyeler her çalıştırmada değişir. Gönderenin kullanılabilir bakiyesi en az 4 TRY olmalıdır. Test temizleme veya volume silme yapmaz.

## Sıradaki doğrulama boşlukları

1. PostgreSQL üzerinde Testcontainers entegrasyon testleri ve eşzamanlı satır kilidi yarışını sınamak.
2. Ağ kesintisi, Outbox çoklu replica yarışı, Kafka tekrar teslimi ve Inbox concurrency davranışını sınamak.
3. Erişilebilirlik için axe/Lighthouse kalite kapısı ve gerçek tarayıcı E2E testi eklemek.
4. OIDC/Keycloak, refresh token rotasyonu, anahtar rotasyonu ve mTLS eklemek.
5. Fonlama muhasebesi eklenmeden üretim kullanımını düşünmemek.
