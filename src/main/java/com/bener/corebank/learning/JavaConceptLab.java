package com.bener.corebank.learning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small executable examples for language questions that do not belong in a banking transaction. */
public final class JavaConceptLab {
    public static final String CURRENCY = "TRY"; // static: belongs to the class; final: cannot be reassigned.

    private JavaConceptLab() {}

    public static Summary summarize(List<String> customerNames) {
        List<String> ordered = new ArrayList<>(customerNames); // List preserves duplicates and order.
        Set<String> unique = new LinkedHashSet<>(ordered);      // Set removes duplicates.
        Map<String, Long> counts = new LinkedHashMap<>();        // Map associates keys with values.
        ordered.forEach(name -> counts.merge(name, 1L, Long::sum));
        long nonBlank = ordered.stream().filter(name -> !name.isBlank()).count();
        return new Summary(List.copyOf(ordered), Set.copyOf(unique), Map.copyOf(counts), nonBlank);
    }

    public static String buildReference(String account, int sequence) {
        return new StringBuilder("TR-").append(account).append('-').append(sequence).toString();
    }

    public static String buildSharedBuffer(String account, int sequence) {
        return new StringBuffer("TR-").append(account).append('-').append(sequence).toString();
    }

    public record Summary(List<String> ordered, Set<String> unique, Map<String, Long> counts, long nonBlank) {}

    /** A record is shallowly immutable; BigDecimal itself is immutable. */
    public record Money(BigDecimal amount, String currency) {
        public Money {
            if (amount == null || amount.signum() < 0 || !CURRENCY.equals(currency)) {
                throw new IllegalArgumentException("Invalid money");
            }
        }
    }

    public interface FeePolicy {
        BigDecimal calculate(BigDecimal amount);
    }

    /** Abstract template: final method fixes the workflow, subclass supplies one step. */
    public abstract static class TransferQuote {
        private final FeePolicy feePolicy; // encapsulated dependency, injected through constructor.

        protected TransferQuote(FeePolicy feePolicy) { this.feePolicy = feePolicy; }

        public final BigDecimal total(BigDecimal amount) {
            return amount.add(feePolicy.calculate(amount)).add(channelFee());
        }

        protected abstract BigDecimal channelFee();
    }

    public static final class OnlineTransferQuote extends TransferQuote {
        public OnlineTransferQuote(FeePolicy feePolicy) { super(feePolicy); }
        @Override protected BigDecimal channelFee() { return BigDecimal.ZERO; }
    }

    public static final class BranchTransferQuote extends TransferQuote {
        public BranchTransferQuote(FeePolicy feePolicy) { super(feePolicy); }
        @Override protected BigDecimal channelFee() { return new BigDecimal("5.00"); }
    }

    public static FeePolicy percentageFee(BigDecimal rate) {
        return amount -> amount.multiply(rate); // Strategy through the interface.
    }

    public sealed interface PaymentOutcome permits Accepted, Rejected, Unknown {}
    public record Accepted(String reference) implements PaymentOutcome {}
    public record Rejected(String reason) implements PaymentOutcome {}
    public record Unknown(String reference) implements PaymentOutcome {}
}
