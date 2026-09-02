package com.renaser.os.notifications.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-7 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): el outbox
 * de Spring Modulith es at-least-once — el MISMO evento de dominio puede entregarsele a un
 * {@code @ApplicationModuleListener} mas de una vez (reintento tras un fallo transitorio, o la
 * republicacion al reiniciar, {@code spring.modulith.events.republish-outstanding-events-on-restart}).
 * Antes de este fix, cada redelivery de {@code HabitoCompletado}/{@code RachaCompletada}/
 * {@code SantuarioRoto}/{@code RocaCompletada} creaba una fila NUEVA en {@code notificaciones}
 * y disparaba un push duplicado — {@link EmitirNotificacionUseCase} no tenia ninguna clave de
 * deduplicacion.
 *
 * <p>Este test ejercita {@link EmitirNotificacionUseCase} directamente (no los 4 listeners uno
 * por uno): los cuatro son traducciones 1:1 al mismo comando (ver sus tests unitarios, que
 * verifican que cada uno propaga su {@code origenEventoId}), asi que probar la deduplicacion
 * una sola vez aca cubre el mecanismo compartido por los cuatro.
 *
 * <p>Requiere Postgres real: la deduplicacion depende del indice unico parcial
 * {@code notificaciones_origen_evento_uk} (V16) y de que Postgres aborte el INSERT en
 * conflicto — no se puede probar con mocks (mismo motivo que
 * {@code PuntajeInicializacionConcurrenciaTest}, C-12, y {@code RocaDiariaConcurrenciaTest}, C-2).
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase: cada llamada a {@code emitir} debe
 * COMMITEAR de verdad (o, en el caso duplicado, aislar su INSERT fallido en su propia
 * transaccion REQUIRES_NEW — ver javadoc de {@code NotificacionService.transaccionPropia}) para
 * que la segunda entrega vea la fila que dejo la primera.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificacionEmitirIdempotenciaIT {

    @Autowired
    private EmitirNotificacionUseCase emitirNotificacionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

    @BeforeEach
    void seedUsuario() {
        usuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Aprendiz de Prueba', 'APRENDIZ')
                """, usuarioId, "aprendiz-" + usuarioId + "@renaser.com");
    }

    @Test
    @DisplayName("C-7: el mismo origenEventoId entregado dos veces -> una sola fila en notificaciones, "
            + "sin excepcion visible para el llamador")
    void mismoOrigenEventoDosVecesNoDuplicaLaFila() {
        UUID origenEventoId = UUID.randomUUID();
        var command = new EmitirNotificacionCommand(UserId.of(usuarioId), TipoNotificacion.HITO_PROGRAMA,
                "Roca completada", "Completaste una Roca Diaria.", null, origenEventoId);

        // El bug (C-7): sin notificaciones_origen_evento_uk + la transaccion propia que atrapa
        // su violacion, esta segunda llamada creaba una segunda fila (y reenviaba el push).
        Optional<Notificacion> primeraEntrega = emitirNotificacionUseCase.emitir(command);
        Optional<Notificacion> redelivery = emitirNotificacionUseCase.emitir(command);

        assertThat(primeraEntrega).as("la primera entrega crea la notificacion").isPresent();
        assertThat(redelivery).as("la redelivery del MISMO evento es idempotente: no crea una segunda fila")
                .isEmpty();

        Long filas = jdbcTemplate.queryForObject(
                "select count(*) from renaser.notificaciones where usuario_id = ? and origen_evento_id = ?",
                Long.class, usuarioId, origenEventoId);
        assertThat(filas).as("exactamente una fila para este origenEventoId, no dos").isEqualTo(1L);
    }

    @Test
    @DisplayName("C-7: entregar el mismo evento una TERCERA vez sigue siendo idempotente")
    void redeliveryRepetidaSigueSiendoIdempotente() {
        UUID origenEventoId = UUID.randomUUID();
        var command = new EmitirNotificacionCommand(UserId.of(usuarioId), TipoNotificacion.SANTUARIO_ROTO,
                "Se rompio tu sesion de Santuario", "Tu sesion se rompio antes de tiempo.", null, origenEventoId);

        emitirNotificacionUseCase.emitir(command);
        emitirNotificacionUseCase.emitir(command);
        emitirNotificacionUseCase.emitir(command);

        Long filas = jdbcTemplate.queryForObject(
                "select count(*) from renaser.notificaciones where usuario_id = ? and origen_evento_id = ?",
                Long.class, usuarioId, origenEventoId);
        assertThat(filas).isEqualTo(1L);
    }

    @Test
    @DisplayName("C-7: eventos DISTINTOS del mismo tipo para el mismo usuario -> cada uno crea su propia fila "
            + "(el indice no deduplica de mas)")
    void origenesDeEventoDistintosNoSeConfunden() {
        var comando1 = new EmitirNotificacionCommand(UserId.of(usuarioId), TipoNotificacion.HITO_PROGRAMA,
                "Roca 1", "c", null, UUID.randomUUID());
        var comando2 = new EmitirNotificacionCommand(UserId.of(usuarioId), TipoNotificacion.HITO_PROGRAMA,
                "Roca 2", "c", null, UUID.randomUUID());

        emitirNotificacionUseCase.emitir(comando1);
        emitirNotificacionUseCase.emitir(comando2);

        Long filas = jdbcTemplate.queryForObject(
                "select count(*) from renaser.notificaciones where usuario_id = ?", Long.class, usuarioId);
        assertThat(filas).isEqualTo(2L);
    }
}
