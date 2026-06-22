package com.example.springobservability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring")
public class ObservabilityController {

    private final OrderService orderService;
    private final Tracer tracer;

    public ObservabilityController(OrderService orderService, Tracer tracer) {
        this.orderService = orderService;
        this.tracer = tracer;
    }

    @GetMapping("/hello/{name}")
    public Map<String, String> hello(@PathVariable String name) {
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("demo.name", name);
        }
        return Map.of(
                "message", "Hello " + name,
                "service", "spring-otel-observability"
        );
    }

    @GetMapping("/order/{customer}")
    public OrderResult order(@PathVariable String customer,
                             @RequestParam(defaultValue = "3") int items) {
        return orderService.createOrder(customer, items);
    }

    @GetMapping("/error")
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> error() {
        IllegalStateException exception = new IllegalStateException("Intentional Spring demo error");
        Span span = tracer.currentSpan();
        if (span != null) {
            span.error(exception);
            span.tag("demo.error", "intentional");
        }
        throw exception;
    }
}
