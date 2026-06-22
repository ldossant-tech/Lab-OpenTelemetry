package com.example.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@ApplicationScoped
public class InventoryService {

    @WithSpan("inventory.reserve")
    public List<String> reserve(int quantity) {
        simulateLatency(80, 220);
        Span.current().setAttribute("demo.inventory.reserved", quantity);
        return IntStream.rangeClosed(1, quantity)
                .mapToObj(index -> "sku-" + (1000 + index))
                .toList();
    }

    @WithSpan("inventory.lookup")
    public int availableItems() {
        simulateLatency(20, 90);
        int available = ThreadLocalRandom.current().nextInt(10, 250);
        Span.current().setAttribute("demo.inventory.available", available);
        return available;
    }

    private void simulateLatency(int minimumMillis, int maximumMillis) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minimumMillis, maximumMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating inventory latency", exception);
        }
    }
}
