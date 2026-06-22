package com.example.springobservability;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResult(
        String orderId,
        String customer,
        int items,
        BigDecimal total,
        String paymentStatus,
        String createdAt
) {

    static OrderResult approved(String orderId, String customer, int items, BigDecimal total) {
        return new OrderResult(orderId, customer, items, total, "approved", Instant.now().toString());
    }
}
