package com.example.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Path("/observability")
@Produces(MediaType.APPLICATION_JSON)
public class ObservabilityResource {

    private static final Logger LOG = Logger.getLogger(ObservabilityResource.class);

    private final CheckoutService checkoutService;
    private final InventoryService inventoryService;

    public ObservabilityResource(CheckoutService checkoutService, InventoryService inventoryService) {
        this.checkoutService = checkoutService;
        this.inventoryService = inventoryService;
    }

    @GET
    @Path("/help")
    public Map<String, Object> help() {
        return Map.of(
                "message", "Use estas rotas para gerar telemetria no OpenShift",
                "routes", Map.of(
                        "GET /observability/hello/{name}", "trace simples + log",
                        "GET /observability/inventory", "span de consulta de estoque",
                        "GET /observability/checkout/{customer}?items=3", "trace com spans de checkout, estoque e pagamento",
                        "GET /observability/slow?ms=800", "span lento para testar latencia",
                        "GET /observability/error", "erro 500 intencional para traces com status de falha"
                )
        );
    }

    @GET
    @Path("/hello/{name}")
    @WithSpan("demo.hello")
    public Map<String, Object> hello(@PathParam("name") String name) {
        Span.current().setAttribute("demo.name", name);
        LOG.infof("Hello endpoint called for %s", name);
        return Map.of(
                "message", "Ola, " + name,
                "traceHint", "Procure pelo servico quarkus-otel-observability no Grafana Explore usando o datasource Tempo",
                "time", Instant.now().toString()
        );
    }

    @GET
    @Path("/inventory")
    @WithSpan("demo.inventory")
    public Map<String, Object> inventory() {
        int available = inventoryService.availableItems();
        LOG.infof("Inventory checked: %d items available", available);
        return Map.of("availableItems", available);
    }

    @GET
    @Path("/checkout/{customer}")
    public CheckoutResult checkout(@PathParam("customer") String customer, @QueryParam("items") @DefaultValue("3") int items) {
        int safeItems = Math.max(1, Math.min(items, 10));
        LOG.infof("Checkout requested by %s with %d items", customer, safeItems);
        return checkoutService.checkout(customer, safeItems);
    }

    @GET
    @Path("/slow")
    @WithSpan("demo.slow-operation")
    public Map<String, Object> slow(@QueryParam("ms") @DefaultValue("750") long millis) {
        long boundedMillis = Math.max(50, Math.min(millis, 5_000));
        Span.current().setAttribute("demo.sleep.ms", boundedMillis);
        Instant started = Instant.now();

        sleep(boundedMillis);

        Duration duration = Duration.between(started, Instant.now());
        LOG.infof("Slow operation finished in %d ms", duration.toMillis());
        return Map.of(
                "requestedMillis", boundedMillis,
                "actualMillis", duration.toMillis(),
                "note", "Use esta rota para enxergar spans com latencia maior"
        );
    }

    @GET
    @Path("/error")
    @WithSpan("demo.intentional-error")
    public Map<String, Object> error() {
        RuntimeException exception = new IllegalStateException("Erro intencional para demonstrar observabilidade");
        Span.current().recordException(exception);
        Span.current().setStatus(StatusCode.ERROR, exception.getMessage());
        LOG.error("Intentional error endpoint called", exception);
        throw new WebApplicationException(exception.getMessage(), 500);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis + ThreadLocalRandom.current().nextLong(1, 80));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating slow operation", exception);
        }
    }
}
