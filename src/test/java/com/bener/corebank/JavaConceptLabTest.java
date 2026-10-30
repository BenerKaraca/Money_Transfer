package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bener.corebank.learning.JavaConceptLab;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class JavaConceptLabTest {
    @Test
    void collectionsAndImmutableSnapshotHaveDifferentSemantics() {
        var input = new ArrayList<>(List.of("Ada", "Ada", "Ece"));
        var summary = JavaConceptLab.summarize(input);
        input.add("Can");
        assertThat(summary.ordered()).containsExactly("Ada", "Ada", "Ece");
        assertThat(summary.unique()).containsExactlyInAnyOrder("Ada", "Ece");
        assertThat(summary.counts()).containsEntry("Ada", 2L);
        assertThat(summary.nonBlank()).isEqualTo(3);
    }

    @Test
    void abstractTemplateAndStrategyAllowPolymorphism() {
        JavaConceptLab.FeePolicy policy = JavaConceptLab.percentageFee(new BigDecimal("0.01"));
        JavaConceptLab.TransferQuote online = new JavaConceptLab.OnlineTransferQuote(policy);
        JavaConceptLab.TransferQuote branch = new JavaConceptLab.BranchTransferQuote(policy);
        assertThat(online.total(new BigDecimal("100.00"))).isEqualByComparingTo("101.00");
        assertThat(branch.total(new BigDecimal("100.00"))).isEqualByComparingTo("106.00");
    }

    @Test
    void stringIdentityAndVirtualThreadAreVisible() throws InterruptedException {
        String literal = "corebank";
        String runtime = new String("corebank");
        assertThat(literal == runtime).isFalse();
        assertThat(literal == runtime.intern()).isTrue();
        assertThat(JavaConceptLab.buildReference("42", 7)).isEqualTo("TR-42-7");
        assertThat(JavaConceptLab.buildSharedBuffer("42", 7)).isEqualTo("TR-42-7");
        AtomicBoolean virtual = new AtomicBoolean();
        Thread thread = Thread.startVirtualThread(() -> virtual.set(Thread.currentThread().isVirtual()));
        thread.join();
        assertThat(virtual).isTrue();
    }

    @Test
    void moneyRejectsInvalidValuesAndSealedOutcomesKeepTheirData() {
        assertThat(new JavaConceptLab.Money(BigDecimal.ZERO, "TRY").amount()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> new JavaConceptLab.Money(null, "TRY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JavaConceptLab.Money(BigDecimal.ONE.negate(), "TRY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JavaConceptLab.Money(BigDecimal.ONE, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JavaConceptLab.Accepted("a").reference()).isEqualTo("a");
        assertThat(new JavaConceptLab.Rejected("r").reason()).isEqualTo("r");
        assertThat(new JavaConceptLab.Unknown("u").reference()).isEqualTo("u");
        assertThat(JavaConceptLab.summarize(List.of("", "Ada")).nonBlank()).isEqualTo(1);
    }
}
