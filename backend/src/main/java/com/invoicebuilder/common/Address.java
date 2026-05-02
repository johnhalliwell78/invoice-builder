package com.invoicebuilder.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Postal address. Persisted as JSONB on tenant and customer rows.
 * All fields optional so partial addresses can be stored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Address(
        String street,
        String city,
        String state,
        String zip,
        String country
) {
}
