package com.invoicebuilder.customer.dto;

import com.invoicebuilder.common.Address;
import com.invoicebuilder.customer.Customer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String company,
        Address address,
        String taxId,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getCompany(),
                c.getAddress(),
                c.getTaxId(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
