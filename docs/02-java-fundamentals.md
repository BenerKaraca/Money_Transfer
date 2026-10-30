# 02 — Java temel konuları ve sürümler

Çalışan örnekler: `JavaConceptLab` ve `JavaConceptLabTest`. Komut: `mvn -Dtest=JavaConceptLabTest test`.

| Sürüm | Bu projede önemli fark | Durum |
| --- | --- | --- |
| Java 15 (LTS değil) | Text blocks kalıcı özellik; record ve `instanceof` pattern matching o sürümde preview idi. | Proje Java 15'e derlenmez. |
| Java 21 (LTS) | Virtual threads, record patterns, `switch` pattern matching ve sequenced collections kalıcı özelliklerdir. Projenin kaynak/bytecode hedefi 21'dir. | Kodda record ve virtual thread örneği var. |
| Java 26 (LTS değil) | HTTP/3 istemcisi eklendi; primitive pattern matching ve structured concurrency hâlâ preview idi. Sınıf dosyası sürümü 70'tir. | Karşılaştırma amaçlı; Java 21 hedefli kod bu API/preview özellikleri kullanmaz. |

Kaynaklar: [Java 15 specification](https://cr.openjdk.org/~dlsmith/8242149/webrev.00/raw_files/new/closed/make/data/docs-resources/specs/index.html), [Virtual Threads JEP 444](https://openjdk.org/jeps/444), [Java 26 release notes](https://www.oracle.com/java/technologies/javase/26-relnote-issues.html), [Oracle destek yol haritası](https://www.oracle.com/java/technologies/java-se-support-roadmap.html).

## Sık sorulan ayrımlar

- `static`: üyeyi sınıfa bağlar. `final`: yeniden atamayı/override etmeyi/kalıtımı bağlama göre yasaklar. `static final CURRENCY` bir sınıf sabitidir; `final` nesne alanı her nesnenin kendi değişmez referansıdır.
- `final class` genişletilemez. `final method` alt sınıfta override edilemez. `TransferQuote.total` finaldir; `OnlineTransferQuote` ve `BranchTransferQuote` da final sınıftır.
- `String` immutable'dır. Literal'ler string pool'da paylaşılabilir; `new String(...)` ayrı nesne üretir. İçerik kıyaslamak için `equals`, referans kıyaslamak için `==` kullanılır.
- `StringBuilder` değiştirilebilir ve kendi başına thread-safe değildir. `StringBuffer` metotları senkronizedir; eşzamanlı kullanım ihtiyacının tamamını tek başına çözmez. İş mantığında gereksiz paylaşılan mutable builder tutulmaz.
- `record` alanları final olduğu için yüzeysel immutable'dır; içinde mutable `List` varsa savunmacı kopya gerekir. `Summary` bu yüzden `List.copyOf`, `Set.copyOf` ve `Map.copyOf` kullanır.
- `List` sıralı ve tekrar kabul eder; `Set` tekrarları kaldırır; `Map` anahtardan değere eşleme yapar. `summarize` Stream API ile boş olmayan isimleri sayar.
- OOP'nin dört başlığı: kapsülleme (`TransferQuote.feePolicy`), soyutlama (`FeePolicy`, `TransferQuote`), kalıtım (iki quote alt sınıfı), çok biçimlilik (`TransferQuote` referansı ile farklı `total` sonucu).
- SOLID örneği: `FeePolicy` ile fiyatlama değişir, `TransferQuote` akışı değişmez (Open/Closed, Dependency Inversion). `percentageFee` bir Strategy örneğidir; repository ve adapter örnekleri diğer modüllerde bulunur.
