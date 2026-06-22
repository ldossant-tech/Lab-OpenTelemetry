package com.example.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CheckoutService {

    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    public CheckoutService(InventoryService inventoryService, PaymentService paymentService) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
    }

    @WithSpan("checkout.create-order")
    public CheckoutResult checkout(String customer, int items) {
        Span.current().setAttribute("demo.customer", customer);
        Span.current().setAttribute("demo.items", items);

        List<String> reservedItems = inventoryService.reserve(items);
        BigDecimal total = calculateTotal(items);
        String paymentId = paymentService.authorize(customer, total);

        return new CheckoutResult(
                "order-" + ThreadLocalRandom.current().nextInt(10_000, 99_999),
                customer,
                reservedItems,
                total,
                paymentId,
                Instant.now().toString()
        );
    }

    @WithSpan("checkout.calculate-total")
    BigDecimal calculateTotal(int items) {
        BigDecimal itemPrice = BigDecimal.valueOf(19.90);
        BigDecimal total = itemPrice.multiply(BigDecimal.valueOf(items));
        Span.current().setAttribute("demo.order.total", total.doubleValue());
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
