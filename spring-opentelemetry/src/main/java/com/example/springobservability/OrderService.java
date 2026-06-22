package com.example.springobservability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final Tracer tracer;

    public OrderService(Tracer tracer) {
        this.tracer = tracer;
    }

    public OrderResult createOrder(String customer, int items) {
        Span span = tracer.nextSpan().name("spring.order.create").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            span.tag("demo.customer", customer);
            span.tag("demo.items", String.valueOf(items));

            simulateLatency(80, 180);
            BigDecimal total = calculateTotal(items);
            authorizePayment(customer, total);

            return OrderResult.approved("spring-order-" + UUID.randomUUID().toString().substring(0, 8),
                    customer, items, total);
        } catch (RuntimeException exception) {
            span.error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private BigDecimal calculateTotal(int items) {
        Span span = tracer.nextSpan().name("spring.order.calculate-total").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            BigDecimal total = BigDecimal.valueOf(29.90)
                    .multiply(BigDecimal.valueOf(items))
                    .setScale(2, RoundingMode.HALF_UP);
            span.tag("demo.order.total", total.toPlainString());
            simulateLatency(20, 70);
            return total;
        } finally {
            span.end();
        }
    }

    private void authorizePayment(String customer, BigDecimal total) {
        Span span = tracer.nextSpan().name("spring.payment.authorize").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            span.tag("demo.payment.customer", customer);
            span.tag("demo.payment.amount", total.toPlainString());
            simulateLatency(120, 280);
        } finally {
            span.end();
        }
    }

    private void simulateLatency(int minimumMillis, int maximumMillis) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minimumMillis, maximumMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating latency", exception);
        }
    }
}
