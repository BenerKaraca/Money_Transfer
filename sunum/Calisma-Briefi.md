# CoreBank çalışma brief'i

Bu sayfayı sunumun yanında açık tut. Önce aşağıdaki anlatımı kendi cümlelerinle söyleyebilir hale gel; sonra soruların yanıtını koda bakmadan ver.

## 60–90 saniyelik anlatım

> CoreBank, bankacılıkta para hareketinin tutarlılığını ve servisler arası hata durumlarını göstermek için geliştirdiğim bir eğitim uygulaması. Aynı veritabanındaki iki hesap arasında transfer yaparken hesapları sabit sırayla kilitliyorum. Bakiye değişikliklerini, iki ledger kaydını ve Outbox olayını tek transaction'da commit ediyorum. Böylece hata olursa bütün işlem geri alınıyor. Harici fatura ödemesinde sağlayıcı ayrı veritabanında olduğu için önce tutarı rezerve edip SAGA ile sonucu yönetiyorum. Sağlayıcı kabul, ret veya timeout sonucu verebilir. Timeout'ta başarı varsaymıyorum; `UNKNOWN` durumunda rezervasyonu koruyup aynı ödeme kimliğiyle mutabakat yapıyorum. Kafka bildirimi Outbox/Inbox ile tekrar güvenli, Redis ise sadece rapor cache'i için. React arayüzü bu akışları gösteriyor.

## Aklında kalacak üç karar

1. **Aynı veritabanı:** `@Transactional` + sabit sırada `PESSIMISTIC_WRITE` kilidi. Transfer yarım kalmaz.
2. **Ayrı ödeme sağlayıcısı:** rezervasyon + idempotent `paymentId` + SAGA. Timeout sonucu `UNKNOWN` kalır ve mutabakat yapılır.
3. **Asenkron bildirim:** transferle birlikte Outbox yazılır; Kafka en az bir kez gönderebilir; Inbox tekrarları eler.

## Sunum yolu

| Slayt | Tek cümlede anlat |
| --- | --- |
| 1–5 | Uygulamanın amacı ve neden çekirdeği modüler monolit, ödeme/bildirimi ayrı servis yaptığın |
| 6–9 | Hesap, rezervasyon, transfer ve ledger modeli; tek transaction; kilit ve idempotency |
| 10–12 | SAGA'nın üç sonucu; timeout belirsizliği; Outbox/Inbox tekrar güvenliği |
| 13–16 | Hibernate N+1, API katmanları, Redis/Redux ve kullanılan teknolojiler |
| 17–19 | Testlerin kanıtladığı şeyler, tasarım bedelleri ve bilinen sınırlar |
| 20–21 | Demo ve üç ana kararın kısa özeti |

## En çok sorulabilecek 10 soru

**1. İki kişi aynı 1.000 TRY'den 800'er TRY gönderirse?**  
Hesap satırı kilitlenir. İlk işlem 800'ü aktarır; ikinci güncel 200 bakiyeyi görür ve rollback olur. Kod: `TransferService`, `AccountRepository`.

**2. İdempotency key neyi çözer?**  
Aynı iş isteğinin tekrarında ikinci para hareketini önler. Aynı anahtar ve aynı içerik mevcut transferi döndürür; farklı içerik 409 alır. Kilit ise farklı eşzamanlı isteklerin bakiye yarışını çözer.

**3. Neden `BigDecimal`?**  
Finansal miktarı ikili kayan nokta hatasından korur. Tutar iki ondalıkla doğrulanır ve PostgreSQL'de `numeric(19,2)` saklanır.

**4. Neden iki ledger satırı?**  
Bir DEBIT göndereni, bir CREDIT alıcıyı kaydeder. İkisi bakiye ve transferle aynı transaction'dadır. Mevcut ledger tam muhasebe defteri değildir; demo açılış bakiyesi ledger olayı değil.

**5. Neden fatura ödemesinde doğrudan bakiyeyi düşürmüyorsun?**  
Sağlayıcı sonucu gelene kadar tutarı `reserved` alanında tutuyorum. Kabulde clearing hesabına transferle düşüyorum; rette rezervasyonu bırakıyorum. Kullanılabilir bakiye `balance − reserved`.

**6. Sağlayıcı kabul edip yanıt kaybolursa?**  
CoreBank `UNKNOWN` yazar, rezervasyonu tutar. Aynı `paymentId` ile idempotent isteği tekrar göndererek kayıtlı sonucu öğrenir; kabulü ikinci kez yaratmaz.

**7. Circuit Breaker para işlemini çözer mi?**  
Hayır. Arızalı HTTP çağrılarını sınırlar ve fallback `UNKNOWN` verir. Finansal sonuç SAGA ve mutabakatla belirlenir.

**8. Neden Outbox ve Inbox birlikte?**  
Outbox, transfer ve olay kaydını tek DB commit'ine alır. Kafka ack sonrası çöküşte tekrar gönderim olabilir; Inbox `event_id` ile aynı bildirimin ikinci kez yazılmasını önler. Uçtan uca exactly-once iddiam yok.

**9. N+1'i nasıl gösterdin ve çözdün?**  
`Account.customer` LAZY. `findAll()` sonrası her müşteri adına erişim ek SQL üretir. `@EntityGraph(customer)` fetch planı tek sorguya indirir; `HibernateFetchingTest` statement sayısını karşılaştırır.

**10. Bu üretime hazır mı?**  
Hayır. Eğitim ve yerel demo. 38 Java ve 4 React testi geçti; ölçülen satır/dal kapsamı %100. Tam Docker Compose ve yerel PostgreSQL kurulumlarında smoke testi geçti. Gerçek PostgreSQL eşzamanlı kilit yarışı, ağ arızası ve tekrar teslim testleri eksik. Kimlik doğrulama/müşteri yetkisi, Outbox claim/izleme ve tam fonlama muhasebesi de yok.

## “Nasıl yaptın?” sorusunda kodu nereden açacaksın?

| Soru | Dosya |
| --- | --- |
| Transfer ve transaction | `src/main/java/com/bener/corebank/transfer/TransferService.java` |
| Kilit | `src/main/java/com/bener/corebank/account/AccountRepository.java` |
| Rezervasyon ve SAGA | `src/main/java/com/bener/corebank/payment/BillPaymentService.java`, `PaymentSagaOrchestrator.java` |
| Timeout ve Circuit Breaker | `src/main/java/com/bener/corebank/payment/HttpPaymentGateway.java` |
| Outbox ve Inbox | `src/main/java/com/bener/corebank/outbox/OutboxDeliveryService.java`, `services/notification/src/main/java/com/bener/notification/NotificationService.java` |
| N+1 | `src/test/java/com/bener/corebank/HibernateFetchingTest.java` |
| Test kanıtı | `src/test/java/com/bener/corebank/TransferIntegrationTest.java`, `BillPaymentSagaTest.java` |
| Frontend state | `frontend/src/store.ts`, `frontend/src/main.tsx` |

## 30 dakikalık prova

1. **5 dakika:** 60–90 saniyelik anlatımı koda bakmadan üç kez söyle.
2. **10 dakika:** Slayt 6–12'yi aç ve transfer, SAGA, Outbox akışlarını kendi cümlelerinle anlat.
3. **10 dakika:** Yukarıdaki 10 soruyu sesli yanıtla. Yanıtın 30–60 saniye sürsün; ardından ilgili kodu açıp göster.
4. **5 dakika:** Üretim sınırlarını ve test kapsamını dürüstçe anlatıp kapanış yap.

**Daha derin çalışma:** `docs/06-mulakat-sunumu-ve-soru-cevap.md` içinde 36 soru-cevap ve demo kontrol listesi var. Listendeki 40 Java/Spring/dağıtık sistem başlığının yerleri `docs/05-coverage.md` içinde.
