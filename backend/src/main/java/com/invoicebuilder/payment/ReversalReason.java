package com.invoicebuilder.payment;

/** Why collected money went back. */
public enum ReversalReason {
    /** Operator returned the money, in full or in part. */
    REFUND,
    /** Customer charged back; the processor withdrew the funds. */
    DISPUTE,
    /** Manual bookkeeping correction. */
    ADJUSTMENT
}
