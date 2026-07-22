package com.invoicebuilder.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recurring_invoice")
@EntityListeners(AuditingEntityListener.class)
public class RecurringInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    /**
     * Day-of-month the schedule is anchored to. Keeps monthly/yearly runs on
     * e.g. the 31st even after passing through shorter months.
     */
    @Column(name = "anchor_day", nullable = false)
    private int anchorDay;

    @Column(name = "next_run", nullable = false)
    private LocalDate nextRun;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "auto_send", nullable = false)
    private boolean autoSend;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 50)
    private String template = "classic";

    /** Payment term applied to each generated draft (due = issue + dueDays). */
    @Column(name = "due_days", nullable = false)
    private int dueDays = 30;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "line_items", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RecurringLineItem> lineItems;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public int getAnchorDay() {
        return anchorDay;
    }

    public void setAnchorDay(int anchorDay) {
        this.anchorDay = anchorDay;
    }

    public LocalDate getNextRun() {
        return nextRun;
    }

    public void setNextRun(LocalDate nextRun) {
        this.nextRun = nextRun;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAutoSend() {
        return autoSend;
    }

    public void setAutoSend(boolean autoSend) {
        this.autoSend = autoSend;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public int getDueDays() {
        return dueDays;
    }

    public void setDueDays(int dueDays) {
        this.dueDays = dueDays;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public List<RecurringLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<RecurringLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
