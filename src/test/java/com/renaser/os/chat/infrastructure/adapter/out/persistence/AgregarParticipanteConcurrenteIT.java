package com.renaser.os.chat.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-265: al aprobar una solicitud como ADMIN/ALCHEMIST, «usuario registrado» y «rol cambiado» suman a la misma persona
 * a los mismos soportes en dos transacciones a la vez. Con «¿existe? → INSERT», las dos veían que no y la segunda
 * reventaba con {@code participantes_conversacion_pkey} (visto en el E2E del 2026-10-13: la publicación del oyente
 * de rol quedaba incompleta en el outbox). Contra Postgres real y con dos transacciones de verdad: la segunda inserta
 * mientras la primera todavía no confirmó.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgregarParticipanteConcurrenteIT {

    @Autowired
    private AgregarParticipantePort agregar;
    @Autowired
    private TransactionTemplate transacciones;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID usuario = UUID.randomUUID();
    private final UUID conversacion = UUID.randomUUID();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.participantes_conversacion WHERE conversacion_id = ?", conversacion);
        jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", conversacion);
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", usuario);
    }

    @Test
    @DisplayName("dos transacciones que suman a la misma persona a la vez: ninguna falla y queda una sola fila")
    void sumarALaVezNoChoca() throws InterruptedException {
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Admin Concurrente', 'ADMIN', 'ACTIVO')
                """, usuario, usuario + "@renaser.test");
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, 'Soporte')",
                conversacion, "soporte|" + conversacion);
        Participante unirse = Participante.unirse(ConversacionId.of(conversacion), UserId.of(usuario), Instant.now());

        CountDownLatch primeraInserto = new CountDownLatch(1);
        CountDownLatch soltarLaPrimera = new CountDownLatch(1);
        AtomicReference<Throwable> falloPrimera = new AtomicReference<>();
        AtomicReference<Throwable> falloSegunda = new AtomicReference<>();

        Thread primera = new Thread(() -> {
            try {
                transacciones.executeWithoutResult(estado -> {
                    agregar.agregar(unirse);
                    primeraInserto.countDown();
                    esperar(soltarLaPrimera);
                });
            } catch (Throwable e) {
                falloPrimera.set(e);
                primeraInserto.countDown();
            }
        });
        Thread segunda = new Thread(() -> {
            try {
                transacciones.executeWithoutResult(estado -> agregar.agregar(unirse));
            } catch (Throwable e) {
                falloSegunda.set(e);
            }
        });

        primera.start();
        assertThat(primeraInserto.await(10, TimeUnit.SECONDS)).isTrue();
        segunda.start();
        Thread.sleep(500); // la segunda llega al INSERT y queda esperando a la primera
        soltarLaPrimera.countDown();
        primera.join(10_000);
        segunda.join(10_000);

        assertThat(falloPrimera.get()).as("la primera transacción").isNull();
        assertThat(falloSegunda.get()).as("la segunda transacción (antes: participantes_conversacion_pkey)").isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participantes_conversacion "
                + "WHERE conversacion_id = ? AND usuario_id = ?", Integer.class, conversacion, usuario)).isEqualTo(1);
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
