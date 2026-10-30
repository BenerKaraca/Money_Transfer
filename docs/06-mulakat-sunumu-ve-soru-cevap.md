# CoreBank teknik mülakat konuşma ve soru-cevap rehberi

Bu rehber, 21 slaytlık sunumun konuşmacı notlarını tamamlar. Sunumda kısa cümleler kullan; ayrıntı istendiğinde aşağıdaki yanıtları kod üzerinden göster. CoreBank eğitim ve yerel demo uygulamasıdır. Gerçek müşteri veya gerçek para için hazır olduğunu söyleme.

## 90 saniyelik açılış

> CoreBank'i bankacılıkta iki farklı tutarlılık problemini göstermek için geliştirdim. Aynı veritabanındaki iki hesap arasındaki transferi tek transaction içinde yapıyorum. Hesapları sabit sırayla kilitleyip bakiyeleri, iki ledger kaydını ve Outbox olayını birlikte commit ediyorum. Böylece yetersiz bakiye veya hata durumunda para hareketi yarım kalmıyor. Harici fatura ödemesinde ise sağlayıcı ayrı veritabanında. Önce tutarı rezerve ediyorum, ardından idempotent sağlayıcı çağrısı ve SAGA ile kabul, ret veya belirsiz sonucu yönetiyorum. Timeout yaşandığında başarı varsaymıyorum; rezervasyonu koruyup aynı kimlikle mutabakat yapıyorum. Kafka bildirimi Outbox ve Inbox ile tekrar güvenli, Redis yalnızca rapor cache'i için kullanılıyor. React arayüzü bu akışları gösteriyor. Yerel PostgreSQL, Kafka ve Redis üzerinde transfer, ödeme, bildirim ve cache smoke testi geçti. Eşzamanlı kilit ve ağ arızası testleri ek çalışma alanı.

## 15 dakikalık anlatım sırası

| Süre | Slayt | Ana fikir |
| --- | --- | --- |
| 0:00–2:00 | 1–5 | Amaç, kullanıcı akışı, modüler çekirdek ve ayrı servis sınırları |
| 2:00–6:00 | 6–9 | Veri modeli, tek transaction, eşzamanlılık, idempotency ve ledger |
| 6:00–9:00 | 10–11 | Harici ödeme SAGA'sı ve timeout sonrası `UNKNOWN` |
| 9:00–11:00 | 12–15 | Outbox/Inbox, Hibernate, HTTP katmanı, Redis ve frontend state |
| 11:00–13:00 | 16–18 | Teknoloji seçimleri, test kanıtı ve ödünleşimler |
| 13:00–15:00 | 19–21 | Bilinen sınırlar, kısa demo ve kapanış |

## Mimari ve ürün soruları

### 1. Bu uygulama tam olarak ne işe yarıyor?

Müşteri ve TRY hesabı oluşturur, iki hesap arasında banka içi transfer yapar, ledger hareketlerini gösterir ve ayrı ödeme sağlayıcısıyla fatura ödeme akışını simüle eder. Başarı, ret ve kayıp yanıt senaryoları özellikle dağıtık sistem davranışını görünür kılar. Arayüz bir demo laboratuvarıdır; mobil veya üretim bankacılığı değildir. Kod: `BankController`, `BillPaymentController`, `frontend/src/main.tsx`.

### 2. Neden bütün sistemi mikroservise bölmedin?

Gönderen, alıcı, transfer ve ledger aynı tutarlılık sınırında. Bunları ayrı servis yaparsam basit ACID transferi gereksiz dağıtık tutarlılık problemine dönüşür. Bu nedenle çekirdek modüler monolit; gerçek ağ ve ayrı veri sahibi gerektiren ödeme sağlayıcısı ile finansal karardan bağımsız bildirim servisi ayrıdır. Kod: `TransferService`, `compose.yaml`.

### 3. Ayrı servislerin avantajı ve bedeli ne?

Bağımsız dağıtım, veri sahipliği ve ilgili yükü ayrı ölçekleme imkânı verir. Bunun bedeli ağ gecikmesi, kısmi hata, tekrar deneme, gözlemlenebilirlik ve dağıtık tutarlılıktır. Mevcut Compose tek replica kurar; otomatik yatay ölçekleme gösterimi yok. Kod: `services/payment-provider`, `services/notification`, `services/gateway`.

### 4. Gateway neden var?

Frontend `/api/**` çağrılarını tek giriş adresine yapar; Spring Cloud Gateway ilgili servise yönlendirir. CoreBank, Spring Security Resource Server olarak JWT imzasını doğrular ve `customer_id` claim'iyle hesap sahipliğini uygular. Gateway yönlendirme katmanıdır; token doğrulamasının finansal serviste de yapılması güven sınırını yalnızca Gateway'e bırakmaz. Hız sınırlama henüz eklenmedi. Kod: `services/gateway/src/main/resources/application.yaml`, `security/SecurityConfiguration.java`.

## Para ve transaction soruları

### 5. Transferi adım adım nasıl yaptın?

Controller isteği DTO ile alır. `TransferService.transfer` anahtar, hesap ve tutarı doğrular, varsa aynı anahtarlı sonucu kontrol eder. Hesapları UUID sırasıyla `PESSIMISTIC_WRITE` kilidiyle okur, kullanılabilir bakiyeyi kontrol eder, kaynak bakiyeyi azaltıp hedefi artırır. Transfer, DEBIT/CREDIT ledger satırları ve Outbox olayını tek `@Transactional` kapsamda kaydeder. Kod: `BankController`, `TransferService`.

### 6. İki 800 TRY isteği aynı 1.000 TRY hesaba aynı anda gelirse?

İlk transaction satır kilidini alır ve 800 TRY aktarır. İkinci transaction kilit açılınca güncel 200 TRY kullanılabilir bakiyeyi görür ve `INSUFFICIENT_FUNDS` nedeniyle rollback olur. H2 üzerinde iki thread testi bir başarının ve tek transferin oluştuğunu doğrular. PostgreSQL kilit davranışı ayrıca gerçek PostgreSQL ile test edilmelidir. Kod: `AccountRepository.findByIdForUpdate`, `TransferIntegrationTest.concurrentWithdrawalsCannotOverdrawAccount`.

### 7. Neden hesapları sabit UUID sırasıyla kilitliyorsun?

A → B ve B → A aynı anda çalışırken biri önce A'yı, diğeri önce B'yi kilitlerse birbirini bekleyebilir. Her transferin iki hesabı aynı global sırayla kilitlemesi bu belirli deadlock olasılığını azaltır. Bütün deadlockları ortadan kaldırdığı iddiası değildir. Kod: `TransferService.perform`.

### 8. `@Version` varken neden pessimistic lock kullandın?

`@Version` iyimser kilitleme için ek güvence sağlar; çakışma ancak yazma anında fark edilir ve retry gerektirebilir. Para çıkışında güncel kullanılabilir bakiyeyi kilit altında okuyup karar vermek daha doğrudan bir akış. Bu örnekte eşzamanlı transferin ana mekanizması `PESSIMISTIC_WRITE`tır. Kod: `Account.version`, `AccountRepository.findByIdForUpdate`.

### 9. İdempotency ile kilit aynı problemi mi çözüyor?

Hayır. İdempotency aynı iş isteğinin ağ veya kullanıcı tekrarında ikinci kez uygulanmasını önler. Satır kilidi farklı isteklerin aynı bakiyeyi eşzamanlı tüketmesini sıraya sokar. `transfers.idempotency_key` unique kısıtı da yarış koşulunda son savunmadır. Kod: `TransferService.matching`, `V1__core_bank.sql`.

### 10. Aynı `Idempotency-Key` farklı tutarla gelirse?

Önceki transferin kaynak, hedef ve tutarı karşılaştırılır. İçerik değişmişse `IDEMPOTENCY_CONFLICT` üretilir ve API 409 döndürür. Aynı içerikse mevcut transfer kimliği döner; para tekrar hareket etmez. Kod: `TransferService.matching`, `ApiExceptionHandler`, `TransferIntegrationTest.repeatedKeyDoesNotMoveMoneyTwiceAndCannotChangePayload`.

### 11. Para için neden `BigDecimal` kullandın?

İkili kayan nokta temsilindeki yuvarlama hatalarını finansal miktara taşımamak için. Veritabanı sütunları `numeric(19,2)`, API ve servis en çok iki ondalığı doğrular. İstemcideki JavaScript `number` finansal otorite değil; backend `BigDecimal` ile son kontrolü yapar. Kod: `TransferService`, `BankController.TransferRequest`, `V1__core_bank.sql`.

### 12. Ledger ile `accounts.balance` arasındaki ilişki ne?

`accounts.balance` hızlı okunan güncel bakiye izdüşümüdür. Her transfer için bir DEBIT ve bir CREDIT ledger satırı oluşturulur, bakiyelerle aynı transaction'da yazılır. Mevcut ledger tam muhasebe defteri değildir: demo açılış bakiyesi ve fonlama için karşı kayıt yoktur. Kod: `TransferService`, `LedgerEntry`, `DemoDataInitializer`.

### 13. Transaction nerede başlıyor ve hangi yazımlar birlikte commit oluyor?

Spring proxy üzerinden çağrılan `TransferService.transfer` metodu `@Transactional`dır. İki Account değişikliği, Transfer, iki LedgerEntry ve OutboxEvent aynı CoreBank veritabanı transaction'ında yazılır. Provider HTTP çağrısı bu transaction içinde yapılmaz; `PaymentSagaOrchestrator` ayrı çağırır. Kod: `TransferService`, `PaymentSagaOrchestrator`.

## SAGA ve dağıtık hata soruları

### 14. Neden fatura ödemesinde önce rezervasyon var?

Sağlayıcı yanıtı beklenirken parayı başka transferin kullanmasını engellemek için. Rezervasyon `reserved` alanını artırır; `balance` henüz değişmez. Kullanılabilir bakiye `balance − reserved` olur. Kabulde rezervasyon çözülür ve clearing hesabına transfer yapılır; rette rezervasyon serbest kalır. Kod: `Account.reserve/release`, `BillPaymentService`.

### 15. Neden SAGA, neden tek `@Transactional` değil?

CoreBank ve ödeme sağlayıcısı ayrı veritabanları kullanır. CoreBank'in yerel transaction'ı sağlayıcının commit'ini atomik olarak kapsayamaz. Bu nedenle rezervasyon, idempotent sağlayıcı çağrısı ve sonucu yerel transaction'larla uygulayan bir SAGA vardır. `REJECTED` durumunda rezervasyonu serbest bırakmak telafi işlemidir. Kod: `BillPaymentService`, `PaymentSagaOrchestrator`.

### 16. Sağlayıcı kabul edip yanıt kaybolursa ne yapıyorsun?

CoreBank `UNKNOWN` yazar ve rezervasyonu tutar. Başarılı veya başarısız diye tahmin etmez. Aynı `paymentId` ile idempotent isteği tekrar gönderir; sağlayıcı önceki kayıtlı sonucu döner. `TIMEOUT_ONCE` demosu provider commit'inden sonra yanıtı geciktirerek bu durumu üretir. Kod: `ProviderPaymentController`, `ProviderPaymentService`, `HttpPaymentGateway`, `BillPaymentSagaTest`.

### 17. Mutabakatta provider'ın `GET status` endpoint'ini mi kullanıyorsun?

Mevcut orchestrator `GET` yerine aynı `paymentId` ile idempotent `POST` isteğini tekrarlar. Provider'da `GET /provider/payments/{id}` endpoint'i de var, fakat bu akışta kullanılmıyor. Mülakatta uygulanmış davranışı böyle anlat. Kod: `PaymentSagaOrchestrator`, `ProviderPaymentController`.

### 18. Circuit Breaker burada tam olarak ne yapıyor?

Provider HTTP çağrısındaki tekrarlayan hataları sınırlamak için Spring Cloud Circuit Breaker kullanılır. Bağlantı timeout'u 1 saniye, okuma timeout'u 2 saniyedir. Çağrı hata verince fallback `UNKNOWN` döndürür; Circuit Breaker finansal sonucu belirlemez. Kod: `HttpPaymentGateway`.

### 19. CAP'i bu projeye nasıl bağlarsın?

Ağ bölünmesinde sağlayıcının sonucuna erişemeyebilirim. CoreBank tutarlı finansal karar için `UNKNOWN` durumunu ve rezervasyonu korur; bu işlem özelinde anında kesin cevap verme imkânından vazgeçer. CAP'i bütün sistemin tek harf seçimi gibi sunma; hangi veri ve hangi ağ sınırında karar verdiğini açıkla. Kod: `HttpPaymentGateway`, `BillPaymentService.applyOutcome`.

### 20. CoreBank kabulden sonra commit edemeden çökerse çift çekim olur mu?

Provider aynı `paymentId` için önceki ACCEPTED sonucunu döner. CoreBank tekrar mutabakat yapar. Yerel clearing transferinin anahtarı `bill-{paymentId}` olduğu için tekrar aynı transferi ikinci kez uygulamaz. Bu mantığın gerçek ağ ve PostgreSQL üzerinde ayrıca uçtan uca test edilmesi gerekir. Kod: `ProviderPaymentService`, `BillPaymentService.applyOutcome`, `TransferService.matching`.

## Kafka, Outbox ve Inbox soruları

### 21. Neden transfer içinde doğrudan Kafka'ya yazmadın?

PostgreSQL commit'i ile Kafka gönderimini tek yerel transaction'a bağlayamam. Biri başarılı diğeri başarısız olursa finansal sonuçla bildirim ayrışır. Bu yüzden Outbox satırını transferle aynı DB transaction'ında saklarım; ayrı yayıncı sonradan Kafka'ya yollar. Kod: `TransferService`, `OutboxDeliveryService`.

### 22. Kafka ack geldikten sonra uygulama çökerse ne olur?

`published_at` commit edilmeden çökebilir. Scheduler aynı olayı yeniden gönderir. Bu nedenle teslimat en az bir kezdir; bildirim servisindeki Inbox `event_id` ile aynı olayı tekrar işlememeye çalışır. Uçtan uca exactly-once iddiası yoktur. Kod: `OutboxDeliveryService`, `NotificationService`.

### 23. Kafka çalışmazsa transfer de başarısız mı olur?

Hayır. Transfer ve Outbox önce CoreBank DB'sinde commit olur. Yayıncı Kafka'ya gönderemezse Outbox satırı bekler ve scheduler sonraki turda tekrar dener. Uzun süreli arızada bekleyen olay sayısı için izleme/uyarı gerekir; mevcut proje bunu tam operasyonel düzeyde kurmaz. Kod: `OutboxScheduler`, `OutboxDeliveryService`.

### 24. Çoklu CoreBank replica'sında Outbox yayıncısı ne yapar?

Mevcut seçimde claim veya `SKIP LOCKED` yok; iki replica aynı olayı gönderebilir. Inbox tekrar bildirimi engellemeye yardımcı olur, fakat gereksiz gönderim kalır. Yatay ölçek hedefinde claim mekanizması, partition ve metrik eklerim. Kod: `OutboxRepository`, `OutboxDeliveryService`.

## Veri erişimi, API ve frontend soruları

### 25. `LAZY` ve `EAGER` farkı, N+1 çözümü ne?

`LAZY` ilişkiyi erişilene kadar erteleyebilir; listedeki her Account için Customer'a erişim ayrı sorgu doğurur. `EAGER` tek başına N+1 garantili çözüm değildir. İhtiyaç duyulan listede `@EntityGraph(customer)` kullanıyorum; test Hibernate statement sayısını ölçerek tek sorguyu doğruluyor. Kod: `Account`, `AccountRepository`, `HibernateFetchingTest`.

### 26. `@PathVariable` ile `@RequestParam` nerede kullanılıyor?

`GET /api/accounts/{accountId}` içindeki hesap kimliği `@PathVariable`. `GET /api/accounts?page=0&size=20` içindeki sayfalama değerleri `@RequestParam`. Controller HTTP sözleşmesini kurar; servis geçerli sayfa aralığını ve iş kurallarını uygular. Kod: `BankController`.

### 27. Aspect, Interceptor ve ControllerAdvice arasındaki fark?

`RequestIdInterceptor` HTTP isteğine request ID ekler. `ServiceTimingAspect` servis metodu süresini loglar. `ApiExceptionHandler` istisnayı standart API hata yanıtına çevirir. Üçü farklı katman ve yaşam döngüsü noktasında çalışır. Kod: `shared/` paketi.

### 28. Redis'te neden bakiye tutmuyorsun?

Bakiye finansal kararın kaynağıdır; kısa süreli eski veri bile yanlış işlem kararına yol açabilir. Redis yalnızca transfer sayısı raporunu 30 saniye cache'ler; raporun kısa süre eski kalması kabul edilen bir ödünleşimdir. Kod: `AccountReportService`, `RedisCacheConfiguration`.

### 29. Redux ile Hooks'un görevleri nasıl ayrılıyor?

Redux Toolkit slice sunucu kaynaklı hesap, transfer ve ödeme durumunu ortak state olarak tutar. Async thunk API çağrılarının pending/fulfilled/rejected durumlarını işler. `useState` form değeri ve idempotency key için; `useEffect` ilk yükleme ve ödeme durumunu periyodik okuma için kullanılır. Kod: `frontend/src/store.ts`, `frontend/src/main.tsx`.

### 30. Frontend HTTP hatasında neden aynı anahtarı koruyor?

İstek sunucuya ulaşmış ve commit olmuş, fakat yanıt tarayıcıya gelmemiş olabilir. Hemen yeni anahtar üretmek ikinci para hareketi riski doğurur. Başarılı yanıttan sonra anahtar yenilenir, hatada aynı kalır. Kod: `frontend/src/main.tsx`.

## Java, kalite ve test soruları

### 31. Java 21'i projede nasıl kullandın, 15 ve 26 nerede?

Maven `java.version=21` ile derler. Record, Stream API ve virtual thread gibi dil örnekleri `JavaConceptLab` ile test edilir. Java 15/21/26 farkları `docs/02-java-fundamentals.md` içinde karşılaştırmadır; proje Java 26 hedefiyle derlenmez. Kod: `pom.xml`, `JavaConceptLab`, `JavaConceptLabTest`.

### 32. OOP, SOLID ve design pattern örneği gösterebilir misin?

`TransferQuote` abstract class ve alt sınıfları kalıtım/çok biçimlilik örneğidir. `FeePolicy` interface bir strateji sınırı; `TransferQuote` template method davranışı gösterir. `PaymentGateway` portu HTTP adaptörünü iş kuralından ayırır. Bu örnekler eğitim laboratuvarıdır; finansal transfer akışının ücret hesapladığını iddia etme. Kod: `JavaConceptLab`, `PaymentGateway`, `HttpPaymentGateway`.

### 33. `static`, `final`, mutable/immutable ve String örnekleri nerede?

`JavaConceptLab` ve testi static/final, final class/final method, record, mutable entity, StringBuilder/StringBuffer ve String Pool farklarını gösterir. `List`, `Set`, `Map` ve Stream örnekleri de oradadır. Ayrıntılı sözlü açıklamalar `docs/02-java-fundamentals.md` içindedir.

### 34. Testler neyi kanıtlıyor, neyi kanıtlamıyor?

CoreBank'te 40 Java testiyle 561/561 satır ve 169/169 dal; React'te 7 testle 152/152 satır ve 113/113 dal kapsandı. Diğer servislerin Docker imajları da kendi %100 kapılarından geçti. Testler transaction, SAGA, Outbox, JWT, 401/403, IDOR koruması, harcama SQL'i, form, tekrar anahtarı ve polling akışlarını sınar. Compose smoke testi JWT, transfer, harcama geçmişi, üç ödeme sonucu, Kafka bildirimi, Outbox ve Redis'i doğruladı. Gerçek PostgreSQL kilit yarışını veya ağ kesintisi/tekrar teslim davranışını bu ölçüm kanıtlamaz. Kod: `src/test`, `services/*/src/test`, `frontend/src/*.test.ts*`, `docs/08-test-ve-calistirma-durumu.md`.

### 35. SonarQube raporunda ne gördün?

Henüz gerçek SonarQube analizi çalıştırılmadı. `sonar-project.properties` ve isteğe bağlı Compose `quality` profili hazır. Çalıştırılmamış analiz için kalite puanı veya sıfır issue iddiasında bulunma. Kod: `sonar-project.properties`, `compose.yaml`.

### 36. Üretime çıkmadan önce ilk değişiklikler neler?

Kimlik doğrulama ve müşteri bazlı yetkilendirme; gerçek PostgreSQL/Testcontainers ve ağ arızası testleri; Outbox claim, retry/uyarı ve operasyonel metrikler. Ardından fonlama ve tam ledger muhasebesi, sayfalı hareket geçmişi ve güvenlik sertleştirmesi. Mevcut sınırlar `README.md` içinde açıkça yazılı.

## Demo kontrol listesi

1. Docker Desktop açıkken `docker compose up --build -d` çalıştır. Ardından `scripts/smoke-test.ps1` ile akışı doğrula. Arayüz 3000, Gateway 8082 portunda açılır.
2. Demo hesabı `20000000-0000-0000-0000-000000000001`, alıcı hesabı `20000000-0000-0000-0000-000000000002`. Demo profilinin başlangıç verisi yalnızca hesap yoksa eklenir; önceki kullanım bakiyeleri değiştirmiş olabilir.
3. Önce `GET /api/accounts/{id}` ile gerçek bakiyeleri oku. Transfer için yeni `Idempotency-Key` kullan.
4. Aynı isteği aynı anahtarla tekrar et; transfer ID'sinin aynı, bakiye ve ledger sayısının değişmediğini göster.
5. `REJECT` fatura ödemesinde `FAILED` ve rezervasyonun çözülmesini göster. `TIMEOUT_ONCE` için `UNKNOWN` anını gösterememe ihtimaline karşı `POST /api/bill-payments/{id}/reconcile` çağrısını hazır tut.

## Sunumda özellikle doğru söyle

- “Transfer için tek veritabanı transaction'ı kullandım.” Harici sağlayıcı için bunu söyleme.
- “Outbox ile en az bir kez teslimat, Inbox ile idempotent işleme hedefledim.” Uçtan uca exactly-once deme.
- “Bakiye PostgreSQL'de; Redis rapor cache'i.” Redis'i finansal otorite olarak anlatma.
- “CoreBank'te 40 Java, React'te 7 test %100 ölçülen satır/dal kapsamıyla geçti; her servis imajı kendi kalite kapısından ve tam Compose smoke testi de JWT dahil geçti.” Gerçek PostgreSQL kilit yarışını veya ağ kesintisini test ettiğini söyleme.
- “Kimlik doğrulama ve müşteri yetkisi henüz yok.” Gerçek bankacılık ürünü deme.
- “Java 21 hedefiyle derleniyor.” Java 26 ile çalıştığını söyleme.

Listendeki 40 başlığın kod, laboratuvar ve belge eşlemesi için `docs/05-coverage.md` dosyasına bak.
