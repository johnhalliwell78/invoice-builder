package com.invoicebuilder.invoice;

import com.invoicebuilder.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceStatusDocTypeTest {

    // ---- INVOICE: the pre-estimate behavior must be unchanged ----

    @Test
    void invoiceKeepsItsLifecycle() {
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(DocType.INVOICE, InvoiceStatus.SENT)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.INVOICE, InvoiceStatus.PAID)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.INVOICE, InvoiceStatus.OVERDUE)).isTrue();
        assertThat(InvoiceStatus.VIEWED.canTransitionTo(DocType.INVOICE, InvoiceStatus.CANCELLED)).isTrue();
    }

    @Test
    void invoiceNeverEntersEstimateStatuses() {
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.INVOICE, InvoiceStatus.APPROVED)).isFalse();
        assertThat(InvoiceStatus.VIEWED.canTransitionTo(DocType.INVOICE, InvoiceStatus.DECLINED)).isFalse();
    }

    // ---- ESTIMATE: DRAFT→SENT→(VIEWED)→APPROVED|DECLINED, cancellable while open ----

    @Test
    void estimateFollowsApprovalLifecycle() {
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.SENT)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.VIEWED)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.APPROVED)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.DECLINED)).isTrue();
        assertThat(InvoiceStatus.VIEWED.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.APPROVED)).isTrue();
        assertThat(InvoiceStatus.VIEWED.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.DECLINED)).isTrue();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.CANCELLED)).isTrue();
    }

    @Test
    void estimateNeverBecomesPaidOrOverdue() {
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.PAID)).isFalse();
        assertThat(InvoiceStatus.SENT.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.OVERDUE)).isFalse();
        assertThat(InvoiceStatus.VIEWED.canTransitionTo(DocType.ESTIMATE, InvoiceStatus.PAID)).isFalse();
    }

    @Test
    void approvedAndDeclinedAreTerminal() {
        for (InvoiceStatus target : InvoiceStatus.values()) {
            assertThat(InvoiceStatus.APPROVED.canTransitionTo(DocType.ESTIMATE, target)).isFalse();
            assertThat(InvoiceStatus.DECLINED.canTransitionTo(DocType.ESTIMATE, target)).isFalse();
        }
    }

    @Test
    void requireTransitionThrowsWithDocTypeContext() {
        assertThatThrownBy(() -> InvoiceStatus.SENT.requireTransition(DocType.ESTIMATE, InvoiceStatus.PAID))
                .isInstanceOf(AppException.class);
    }
}
