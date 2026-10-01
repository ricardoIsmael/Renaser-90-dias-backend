package com.renaser.os.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-473: con el pool de Hikari agotado la API respondía 500 genérico. Ahora responde 503 con
 * {@code Retry-After} y un mensaje que la app puede mostrar, sin el detalle del pool. Se prueba por el
 * despacho real de Spring MVC (los dos advices juntos, como en producción), con la excepción tal como la
 * arma Hikari y envuelta como llega por cada camino.
 */
class ServidorOcupadoHandlerTest {

    /** El texto literal de Hikari en la prueba de estrés (D-238). */
    private static final String DETALLE_DEL_POOL = "RenaserHikari - Connection is not available, request timed out "
            + "after 5245ms (total=20, active=20, idle=0, waiting=341)";

    @RestController
    static class Endpoints {

        @GetMapping("/transaccion")
        String transaccion() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                    new SQLTransientConnectionException(DETALLE_DEL_POOL));
        }

        @GetMapping("/jdbc")
        String jdbc() {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                    new SQLTransientConnectionException(DETALLE_DEL_POOL));
        }

        @GetMapping("/hibernate")
        String hibernate() {
            throw new TransientDataAccessResourceException("could not prepare statement",
                    new org.hibernate.exception.GenericJDBCException("could not prepare statement",
                            new SQLTransientConnectionException(DETALLE_DEL_POOL)));
        }

        @GetMapping("/otra-falla-de-base")
        String otraFalla() {
            throw new CannotCreateTransactionException("otra cosa", new RuntimeException("no es el pool"));
        }

        /** Una causa que {@link GlobalExceptionHandler} ya mapeaba (ISE → 409) tiene que seguir igual. */
        @GetMapping("/causa-ya-mapeada")
        String causaYaMapeada() {
            throw new CannotCreateTransactionException("otra cosa", new IllegalStateException("estado"));
        }

        @GetMapping("/conflicto")
        String conflicto() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }
    }

    /** Registrados al revés a propósito: el orden lo tiene que poner {@code @Order}, no el registro. */
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new ServidorOcupadoHandler(), new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("E-473: sin conexión del pool → 503 con Retry-After y un mensaje legible, por los tres caminos")
    void poolAgotadoEs503() throws Exception {
        for (String ruta : new String[] {"/transaccion", "/jdbc", "/hibernate"}) {
            mvc.perform(get(ruta))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(
                            "El servidor está ocupado en este momento. Intenta de nuevo en unos segundos."))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("Hikari"))));
        }
    }

    @Test
    @DisplayName("E-473: una falla de base que NO es el pool sigue el camino de siempre (no se disfraza de 503)")
    void otraFallaNoSeVuelve503() {
        assertThatThrownBy(() -> mvc.perform(get("/otra-falla-de-base")))
                .hasRootCauseMessage("no es el pool");
    }

    @Test
    @DisplayName("E-473: lo que GlobalExceptionHandler ya mapeaba por causa sigue igual (el nuevo advice va después)")
    void loQueYaSeMapeabaPorCausaNoCambia() throws Exception {
        mvc.perform(get("/causa-ya-mapeada")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("E-473: el 409 de una violación de integridad no cambia")
    void elConflictoSigueSiendo409() throws Exception {
        mvc.perform(get("/conflicto")).andExpect(status().isConflict());
    }
}
