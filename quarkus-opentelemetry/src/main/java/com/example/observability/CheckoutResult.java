package com.example.observability;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutResult(
        String orderId,
        String customer,
        List<String> items,
        BigDecimal total,
        String paymentId,
        String createdAt
) {
}
