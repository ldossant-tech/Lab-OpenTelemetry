package com.example.observability;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ObservabilityResourceTest {

    @Test
    void helpListsDemoRoutes() {
        given()
                .when().get("/observability/help")
                .then()
                .statusCode(200)
                .body("message", containsString("telemetria"));
    }

    @Test
    void checkoutCreatesOrder() {
        given()
                .when().get("/observability/checkout/ana?items=2")
                .then()
                .statusCode(200)
                .body("customer", equalTo("ana"))
                .body("orderId", notNullValue())
                .body("items.size()", equalTo(2));
    }
}
