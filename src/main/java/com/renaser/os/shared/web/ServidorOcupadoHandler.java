package com.renaser.os.shared.web;

import org.hibernate.JDBCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLTransientConnectionException;
import java.util.Optional;

/**
 * E-473: el pool de conexiones a la base (Hikari) no dio conexión a tiempo → <b>503</b> con
 * {@code Retry-After}, no 500.
 *
 * <p><b>Por qué.</b> En la prueba de estrés (D-238), desde ~450 usuarios a la vez, el pool se agotó
 * ({@code RenaserHikari - Connection is not available, request timed out after 5245ms}) y la API
 * respondió miles de 500. La saturación es esperable; la forma no: un 500 dice "bug" a la app y a las
 * alertas, y un 503 dice "ocupado, reintenta". La app muestra el {@code message} del cuerpo
 * ({@code mensajeDeError} en {@code apiClient.ts}), así que el texto está pensado para una persona.
 *
 * <p><b>Cómo se reconoce.</b> Hikari siempre lanza {@link SQLTransientConnectionException} cuando no
 * entrega una conexión a tiempo; según por dónde se pidió llega envuelta en
 * {@link CannotCreateTransactionException} (al abrir un {@code @Transactional}),
 * {@code CannotGetJdbcConnectionException} (una {@link DataAccessResourceFailureException}, desde
 * {@code JdbcClient}/{@code JdbcTemplate}), {@link TransientDataAccessResourceException} o un
 * {@link JDBCException} de Hibernate. Se responde 503 solo si en la cadena de causas está esa
 * excepción; si no, se relanza la misma excepción y sigue el camino de siempre (500), sin cambios.
 *
 * <p><b>Clase aparte de {@link GlobalExceptionHandler}</b> porque aquella ya pasa el techo de tamaño
 * de la regla 01. <b>Se consulta DESPUÉS</b> que aquella ({@code @Order}): Spring prueba los advices en
 * orden y cada uno también mira las causas, así que si esta fuera primero, al relanzar le quitaría a
 * {@link GlobalExceptionHandler} la oportunidad de mapear por causa lo que hoy mapea. Así, todo lo que
 * antes tenía respuesta la sigue teniendo igual, y esta solo atiende lo que antes terminaba en 500. El
 * detalle del pool (activas, esperando) queda en el log, nunca viaja al cliente.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ServidorOcupadoHandler {

    static final String MENSAJE = "El servidor está ocupado en este momento. Intenta de nuevo en unos segundos.";

    /** Corto a propósito: la saturación de la prueba se vaciaba en segundos al bajar la carga. */
    static final String REINTENTAR_EN_SEGUNDOS = "5";

    private static final Logger log = LoggerFactory.getLogger(ServidorOcupadoHandler.class);

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            TransientDataAccessResourceException.class, JDBCException.class, SQLTransientConnectionException.class})
    public ResponseEntity<ApiErrorResponse> handleSinConexionALaBase(Exception ex) throws Exception {
        SQLTransientConnectionException sinConexion = sinConexionEn(ex).orElseThrow(() -> ex);
        log.warn("503 -> pool de base agotado (Retry-After {}s): {}", REINTENTAR_EN_SEGUNDOS, sinConexion.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, REINTENTAR_EN_SEGUNDOS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(MENSAJE));
    }

    /** La excepción de "no hay conexión disponible" en la cadena de causas, si está. */
    private static Optional<SQLTransientConnectionException> sinConexionEn(Throwable ex) {
        for (Throwable actual = ex; actual != null; actual = actual.getCause() == actual ? null : actual.getCause()) {
            if (actual instanceof SQLTransientConnectionException sinConexion) {
                return Optional.of(sinConexion);
            }
        }
        return Optional.empty();
    }
}
