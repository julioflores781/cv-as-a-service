package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminSessionFilterTest {

    @Inject
    AdminTestSupport support;

    @BeforeEach
    void setUp() {
        support.resetAccount();
        ThreadRecordingAccountRepository.READ_THREADS.clear();
        ThreadRecordingAccountRepository.failReads = false;
    }

    @AfterEach
    void tearDown() {
        ThreadRecordingAccountRepository.failReads = false;
    }

    @Test
    void theSessionCheckDoesNotRunOnTheEventLoop() { // T89 (parte 1)
        given().header("Authorization", "Bearer " + support.token(1)).when().get("/api/v1/admin/auth/session")
                .then().statusCode(200);
        assertFalse(ThreadRecordingAccountRepository.READ_THREADS.isEmpty(), "el filtro debe consultar la BD");
        for (String thread : ThreadRecordingAccountRepository.READ_THREADS) {
            assertFalse(thread.contains("eventloop"), "consulta bloqueante en el hilo de eventos: " + thread);
        }
    }

    @Test
    void withTheDatabaseDownTheAnswerIsAGeneric500ThatLeaksNothing() { // T89 (parte 2)
        String token = support.token(1);
        ThreadRecordingAccountRepository.failReads = true;
        String body = given().header("Authorization", "Bearer " + token).when().get("/api/v1/admin/auth/session")
                .then().statusCode(500)
                .body("code", equalTo("UNEXPECTED"))
                .body("detail", not(org.hamcrest.Matchers.containsString("hunter2")))
                .extract().asString();
        assertTrue(!body.contains("jdbc") && !body.contains("hunter2"), body);
    }
}
