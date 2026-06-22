package com.example.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class PaymentService {

    @WithSpan("payment.authorize")
    public String authorize(String customer, BigDecimal amount) {
        simulateLatency();
        Span.current().setAttribute("demo.payment.customer", customer);
        Span.current().setAttribute("demo.payment.amount", amount.doubleValue());
        return "pay-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(120, 380));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating payment latency", exception);
        }
    }
}
