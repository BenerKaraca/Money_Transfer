# 03 — Hibernate yükleme ve Spring web akışı

`Account.customer` ilişkisi `LAZY` tanımlıdır. `findAll()` sonrası her ayrı müşterinin adına erişmek yeni SQL üretir; bu, N+1 örneğidir. `AccountRepository.findAllWithCustomer()` aynı veriyi `@EntityGraph` ile tek sorguda çeker. `HibernateFetchingTest`, Hibernate istatistiklerinden gerçek statement sayısını karşılaştırır. Üretim API'sindeki `GET /api/accounts?page=0&size=20` de müşteri adını sayfalı ve entity graph ile okur.

`LAZY`, ilişkiyi erişilene kadar ertelemeye yarar; her zaman daha hızlı olduğu anlamına gelmez. `EAGER` de N+1'i otomatik çözmez. Ekranın ihtiyacına göre `JOIN FETCH`, `@EntityGraph` veya DTO projection seçilir. `spring.jpa.open-in-view=false` olduğundan DTO eşleme transaction içinde yapılır.

İstek akışı: `RequestIdInterceptor` HTTP başlığı üretir → `BankController` `@PathVariable` ile kaynak kimliği, `@RequestParam` ile sayfa parametresi alır → servis ve Spring Data repository → `ServiceTimingAspect` servis süresini DEBUG seviyesinde loglar → `ApiExceptionHandler` hataları tutarlı biçimde döndürür. Interceptor HTTP seviyesindedir; Aspect servis metodu seviyesindedir; ControllerAdvice hata çevirir. Bunlar aynı görev değildir.

Veritabanı normalizasyonu: müşteri, hesap, transfer ve ledger ayrı tablolardır. FK'ler ilişkiyi; unique/check kısıtları veri bütünlüğünü korur. `accounts.balance` performans için kasıtlı bir türetilmiş değerdir ve transfer ile aynı transaction'da güncellenir.
