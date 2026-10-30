# 05 — İstenen 40 başlığın eşlemesi

Bu tablo, listedeki tekrarları da ayrı satır sayar. **Kod** çalışan uygulama veya test; **Lab** küçük çalıştırılabilir Java örneği; **Belge** teknik karşılaştırma demektir. Bir satırın bulunması, bütün mülakat varyasyonlarının tamamlandığı anlamına gelmez.

| # | Başlık | Nerede |
| ---: | --- | --- |
| 1 | Java 15 / 21 / 26 farkları | `docs/02-java-fundamentals.md`; Java 21 derleme hedefi, record/virtual thread labı |
| 2 | Temel Java | `JavaConceptLab` ve `JavaConceptLabTest` |
| 3 | Stream API | `JavaConceptLab.summarize`, hesap API'si, testler |
| 4 | Collections | `JavaConceptLab.summarize` — sıralama ve tekrar semantiği |
| 5 | `static` / `final` farkı | `JavaConceptLab.CURRENCY`, `docs/02-java-fundamentals.md` |
| 6 | Spring Cloud | `services/gateway`, CoreBank Circuit Breaker |
| 7 | `final class` / `final method` | `OnlineTransferQuote`, `BranchTransferQuote`, `TransferQuote.total` |
| 8 | SonarQube | `sonar-project.properties`, Compose `quality` profili, `docs/04-distributed-and-quality.md` |
| 9 | StringBuilder / StringBuffer | `JavaConceptLab.buildReference` / `buildSharedBuffer`, lab testi |
| 10 | Immutable / mutable | `Money` ve `Summary` recordları; `Account` entity; `docs/02-java-fundamentals.md` |
| 11 | String Pool | `JavaConceptLabTest.stringIdentityAndVirtualThreadAreVisible` |
| 12 | List / Set / Map | `JavaConceptLab.summarize` ve testi |
| 13 | Anotasyonlar | Spring MVC, JPA, validation ve test anotasyonları |
| 14 | `@PathVariable` / `@RequestParam` | `BankController.getAccount` / `listAccounts` |
| 15 | Hibernate nasıl veri çeker? | `HibernateFetchingTest`, `docs/03-hibernate-and-web.md` |
| 16 | Servis oluşturma örneği | `AccountService`, `TransferService`, `BillPaymentService` |
| 17 | Outbox | `OutboxEvent`, `OutboxDeliveryService`, `V2__outbox.sql` |
| 18 | SAGA | `PaymentSagaOrchestrator`, `BillPaymentSagaTest` |
| 19 | Lazy / Eager | `Account.customer` LAZY, `@EntityGraph` ile eager fetch planı, belge |
| 20 | N+1 | `HibernateFetchingTest` gerçek SQL statement sayısını karşılaştırır |
| 21 | Entity'ler | `Account`, `Customer`, `Transfer`, `LedgerEntry`, `BillPayment` |
| 22 | AOP / Aspect / Interceptor / ControllerAdvice | `ServiceTimingAspect`, `RequestIdInterceptor`, `ApiExceptionHandler` |
| 23 | Transaction | `TransferService`, `BillPaymentService`, Outbox ve testleri |
| 24 | Spring Data / Spring Cloud | JPA repository'ler; Gateway ve Circuit Breaker |
| 25 | Mikroservisler / scaling | Ayrı ödeme, bildirim ve gateway uygulamaları; Compose; `docs/04-distributed-and-quality.md` |
| 26 | Monolit servisler | CoreBank çekirdeğindeki hesap/transfer/payment modülleri |
| 27 | Servis avantajları / dezavantajları | `docs/04-distributed-and-quality.md` |
| 28 | SAGA pattern (tekrar) | `PaymentSagaOrchestrator`, telafi ve belirsiz sonuç testleri |
| 29 | CAP / Circuit Breaker | `HttpPaymentGateway`; ağ kesintisi ve tutarlılık tartışması belgede |
| 30 | OOP'nin dört başlığı | `TransferQuote`, `FeePolicy`, iki alt sınıf ve lab belgesi |
| 31 | Abstract / Interface | `TransferQuote` abstract class, `FeePolicy` ve `PaymentGateway` interface |
| 32 | Temiz kod / SOLID | Port-adapter ayrımı, constructor injection; `docs/02-java-fundamentals.md` |
| 33 | Design pattern | Strategy (`FeePolicy`), Template Method (`TransferQuote`), Adapter (`HttpPaymentGateway`), Repository, Outbox |
| 34 | Normalizasyon | Flyway müşteri/hesap/transfer/ledger ve FK şeması |
| 35 | Redis | Compose Redis servisi ve `RedisCacheConfiguration` |
| 36 | Cache'leme | `AccountReportService.transferCount`, 30 saniye TTL; bakiye cache'lenmez |
| 37 | State yönetimi | `frontend/src/store.ts` |
| 38 | Redux | Redux Toolkit async thunk ve slice |
| 39 | Hooks | `frontend/src/main.tsx` içinde `useState`, `useEffect`, `useSelector`, `useDispatch` |
| 40 | JUnit | Transfer, SAGA, Hibernate, Java labı, sağlayıcı ve bildirim testleri |

## Doğrulama sınırı

Java ve frontend modülleri yerel test/derleme ile doğrulanır. Kafka, Redis, üç PostgreSQL veritabanı ve Gateway arasındaki gerçek ağ akışı yalnızca `docker compose up --build` ile doğrulanabilir. `sonar-project.properties` analize hazırdır; SonarQube sunucusu ve token olmadan analiz sonucu üretilmez.
