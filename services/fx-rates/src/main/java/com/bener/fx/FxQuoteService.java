package com.bener.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FxQuoteService {
    private static final Set<String> CURRENCIES = Set.of("TRY", "EUR", "USD");
    private final ReferenceRateProvider provider;
    private final Clock clock;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();

    public FxQuoteService(ReferenceRateProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public List<ReferenceRateProvider.Rate> rates() {
        return List.of(provider.rate("EUR", "TRY"), provider.rate("USD", "TRY"), provider.rate("EUR", "USD"));
    }

    public Quote quote(String source, String target, BigDecimal amount) {
        if (!CURRENCIES.contains(source) || !CURRENCIES.contains(target) || source.equals(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz döviz çifti");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz tutar");
        }
        ReferenceRateProvider.Rate rate = provider.rate(source, target);
        if (rate == null || rate.rate() == null || rate.rate().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kur sağlayıcısı geçersiz yanıt döndü");
        }
        BigDecimal sourceAmount = amount.setScale(2);
        BigDecimal targetAmount = sourceAmount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP);
        if (targetAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dönüşüm tutarı çok küçük");
        }
        Quote result = new Quote(UUID.randomUUID(), source, target, sourceAmount, targetAmount,
                rate.rate(), rate.date(), Instant.now(clock).plusSeconds(120));
        quotes.put(result.id(), result);
        return result;
    }

    public Quote find(UUID id) {
        Quote quote = quotes.get(id);
        if (quote == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Teklif bulunamadı");
        }
        if (!Instant.now(clock).isBefore(quote.expiresAt())) {
            quotes.remove(id);
            throw new ResponseStatusException(HttpStatus.GONE, "Teklifin süresi doldu");
        }
        return quote;
    }

    public record Quote(UUID id, String sourceCurrency, String targetCurrency, BigDecimal sourceAmount,
                        BigDecimal targetAmount, BigDecimal rate, LocalDate rateDate, Instant expiresAt) {}
}
