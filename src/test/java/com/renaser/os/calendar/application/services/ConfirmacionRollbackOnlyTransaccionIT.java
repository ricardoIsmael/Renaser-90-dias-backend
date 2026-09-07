package com.renaser.os.calendar.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regresion de C-15 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): antes,
 * un fallo al cancelar los avisos pendientes (best-effort — ver javadoc de
 * {@code ConfirmacionService.cancelarAvisosDeAsistencia}) se atrapaba con un warn, pero para
 * entonces la transaccion de {@code confirmar()} ya habia quedado marcada rollback-only por el
 * advice transaccional del propio metodo que fallo — el commit posterior explotaba con
 * {@code UnexpectedRollbackException}, un error que no dice nada de la causa real.
 *
 * <p>Requiere Postgres real (no un mock): lo que se demuestra es que la transaccion de
 * {@code confirmar()} efectivamente COMITEA — con un mock nunca hay un commit real que pueda
 * fallar, asi que la aserción "la confirmacion quedo persistida" pasaria igual con el codigo
 * viejo (que interceptaba la excepcion antes de que rompiera nada visible en un test con
 * mocks) y con el nuevo. Deliberadamente SIN {@code @Transactional} de clase por el mismo
 * motivo que {@code EvidenciaProcesarLoteTransaccionIT}: esa anotacion abriria una transaccion
 * ambiente ANTES de invocar el caso de uso, ocultando cualquier problema de manejo de
 * transacciones del propio {@code confirmar()}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ConfirmacionRollbackOnlyTransaccionIT.RecordatorioQueFallaConfig.class})
class ConfirmacionRollbackOnlyTransaccionIT {

    /**
     * Ocurrencia del evento del fixture, <b>anclada al reloj y no escrita a mano</b>.
     *
     * <p>Antes decia {@code Instant.parse("2026-09-05T19:00:00Z")}, y eso era una bomba de tiempo.
     * Esta prueba corre con el <b>reloj real</b> (es un IT, no usa {@code FixedClock}), y
     * {@code ConfirmacionService.confirmar} rechaza toda ocurrencia anterior al arranque del dia
     * de hoy en UTC menos {@code MARGEN_OCURRENCIA_PASADA_HORAS} (12 h). Con esa fecha fija la
     * prueba pasaba mientras "hoy" fuera el 5 o el 6 de septiembre de 2026 y <b>empezaba a fallar
     * sola el 7</b>, sin que nadie tocara una linea.
     *
     * <p>Dos horas hacia adelante: siempre dentro de la ventana permitida, corra cuando corra la
     * suite, y ademas es el caso realista — se confirma asistencia a un evento que todavia no
     * ocurrio. El instante se calcula una sola vez y toda la clase lo comparte, asi el INSERT del
     * fixture y la confirmacion hablan de la misma ocurrencia.
     */
    private static final Instant INICIA_EN = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS);

    @Autowired
    private ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RecordatorioQueFalla recordatorioQueFalla;

    private UUID actorId;
    private UUID eventoId;

    @BeforeEach
    void seedUsuarioParticipanteYEvento() {
        recordatorioQueFalla.fallar.set(false);
        recordatorioQueFalla.intentos.set(0);

        actorId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """,
                actorId, actorId + "@renaser.test");
        jdbcTemplate.update("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                        VALUES (?, 20, 'America/Lima')
                        """,
                actorId);

        eventoId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.eventos (id, titulo, inicia_en, duracion_minutos, timezone,
                                                     tipo_ubicacion, tipo_audiencia, tipo_evento, estado)
                        VALUES (?, 'Evento fixture', ?, 60, 'America/Lima',
                                CAST('MEET' AS renaser.tipo_ubicacion), CAST('TODOS' AS renaser.tipo_audiencia),
                                CAST('ESPONTANEO' AS renaser.tipo_evento_calendario), CAST('PUBLICADO' AS renaser.estado_evento))
                        """,
                eventoId, Timestamp.from(INICIA_EN));
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.confirmaciones_evento WHERE evento_id = ?", eventoId);
        jdbcTemplate.update("DELETE FROM renaser.eventos WHERE id = ?", eventoId);
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", actorId);
    }

    @Test
    @DisplayName("C-15: si cancelar los avisos falla, confirmar() igual commitea -- sin UnexpectedRollbackException")
    void confirmarSobreviveAUnFalloAlCancelarAvisos() {
        recordatorioQueFalla.fallar.set(true);

        assertThatCode(() -> confirmarAsistenciaUseCase.confirmar(UserId.of(actorId), EventoId.of(eventoId),
                INICIA_EN, EstadoConfirmacion.ASISTE)).doesNotThrowAnyException();

        Long confirmaciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.confirmaciones_evento WHERE evento_id = ? AND usuario_id = ?",
                Long.class, eventoId, actorId);
        assertThat(confirmaciones).as("la confirmacion se persistio (la transaccion comiteo) pese al fallo al "
                + "cancelar los avisos").isEqualTo(1);
        assertThat(recordatorioQueFalla.intentos.get()).as("se intento cancelar los avisos exactamente una vez")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Control: sin fallo, confirmar() cancela los avisos y persiste la confirmacion igual que siempre")
    void confirmarSinFalloSigueFuncionandoIgual() {
        confirmarAsistenciaUseCase.confirmar(UserId.of(actorId), EventoId.of(eventoId), INICIA_EN,
                EstadoConfirmacion.ASISTE);

        Long confirmaciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.confirmaciones_evento WHERE evento_id = ? AND usuario_id = ?",
                Long.class, eventoId, actorId);
        assertThat(confirmaciones).isEqualTo(1);
        assertThat(recordatorioQueFalla.intentos.get()).isEqualTo(1);
    }

    @TestConfiguration
    static class RecordatorioQueFallaConfig {

        @Bean
        @Primary
        RecordatorioQueFalla recordatorioQueFalla() {
            return new RecordatorioQueFalla();
        }
    }

    /**
     * Implementa AMBOS puertos del adaptador real ({@code RecordatorioPersistenceAdapter}
     * implementa {@link LoadRecordatorioPort} y {@link SaveRecordatorioPort} a la vez) para no
     * romper el autowiring de {@code RecordatorioService} (depende de
     * {@link LoadRecordatorioPort}) al reemplazar el bean con {@code @Primary}. Solo
     * {@code cancelarPorAsistencia} hace algo interesante (contar intentos y, si
     * {@code fallar} esta prendido, lanzar); el resto son no-ops que esta prueba no ejercita.
     */
    static class RecordatorioQueFalla implements LoadRecordatorioPort, SaveRecordatorioPort {

        final AtomicInteger intentos = new AtomicInteger();
        final AtomicBoolean fallar = new AtomicBoolean(false);

        @Override
        public List<RecordatorioEvento> vencidosPendientes(Instant hasta, int limite) {
            return List.of();
        }

        @Override
        public int encolarSiFalta(List<RecordatorioEvento> recordatorios) {
            return 0;
        }

        @Override
        public void marcarEnviados(List<Long> ids, Instant enviadoEn) {
            // no-op: esta prueba no lo ejercita.
        }

        @Override
        public int cancelarPorIds(List<Long> ids, String motivo) {
            return 0;
        }

        @Override
        public int cancelarPorAsistencia(UserId usuarioId, EventoId eventoId, Instant inicioOcurrencia,
                                          String motivo) {
            intentos.incrementAndGet();
            if (fallar.get()) {
                throw new IllegalStateException("fallo simulado de C-15: no se pudo cancelar el aviso");
            }
            return 0;
        }

        @Override
        public int cancelarPorOcurrencia(EventoId eventoId, Instant inicioOcurrencia, String motivo) {
            return 0;
        }

        @Override
        public int borrarPendientesFuturos(EventoId eventoId, Instant ahora) {
            return 0;
        }
    }
}
