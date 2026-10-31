package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.api.AudioterapiaDelAprendizPort;
import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E-652 (issue #33), con base real: la Pastilla Renacer y la Audioterapia semanal no se cierran por
 * {@code POST /habit-tracks/{id}/complete} sin el audio ni las respuestas, y sus caminos validos siguen cerrando.
 *
 * <p><b>Por que Postgres real.</b> Los tests unitarios de {@code RegistroServiceTest} fijan la decision con un
 * {@code RegistrosConTextoFinder} falso; lo que solo se ve aqui es (1) que las politicas nuevas se registran en
 * Spring, (2) que la consulta JPQL {@code LENGTH(TRIM(contenido_texto)) >= :largoMinimo} mide lo que debe sobre la
 * tabla {@code evidencias} de verdad, y (3) que el rechazo no deja ni un punto ni un asiento en el ledger.
 *
 * <p><b>Usa los habitos REALES del catalogo (V4)</b>, como {@code ClaseDiariaYPastillaCerrojoIT}: la
 * {@code clave_sistema} es lo que las politicas leen. Reloj fijo a las 10:00Z (05:00 en Lima, por delante del ancla de
 * los dos horarios de V4) para que ambos paguen 10 puntos completos a cualquier hora que corra la suite. Sin
 * {@code @Transactional} de clase: cada paso abre su propia transaccion, como en produccion (subir la evidencia y
 * completar son dos pedidos).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PastillaYAudioterapiaConRespuestasIT {

    /** 10:00Z = 05:00 en Lima: por delante del ancla de los dos habitos. */
    private static final Instant AHORA = Instant.parse("2026-08-24T10:00:00Z");
    private static final LocalDate DIA = AHORA.atZone(ZoneId.of("America/Lima")).toLocalDate();
    /** Dentro del rango de los horarios de V4: la Audioterapia rige del dia 11, la Pastilla del 8. */
    private static final int DIA_PROGRAMA = 20;
    private static final int SALDO_INICIAL = 100;
    private static final int PUNTOS_DEL_HABITO = 10;

    private static final String MENSAJE_PASTILLA =
            "Para la Pastilla Renacer, escucha el audio y responde las dos preguntas.";
    private static final String MENSAJE_AUDIOTERAPIA =
            "Para la Audioterapia semanal, escucha el audio y responde las dos preguntas.";

    /** El formato que arma la app: cada pregunta con su respuesta debajo, separadas por una linea en blanco. */
    private static final String DOS_RESPUESTAS = """
            ¿Qué sentiste después de escuchar este audio?
            Sentí mucha calma en el pecho.

            ¿Qué te llevas de este audio para tu día de hoy?
            Me llevo respirar antes de contestar.""";

    /** Fija el puntaje de los dos habitos en 10 a cualquier hora (ver javadoc de la clase). */
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CompletarRegistroUseCase completarRegistro;
    @Autowired
    private SubirEvidenciaRegistroUseCase subirEvidencia;
    @Autowired
    private CompletarPastillaRenacerUseCase completarPastillaRenacer;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participanteId;

    @BeforeEach
    void seedFixtures() {
        reloj.fijar(AHORA);
        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture respuestas del audio', 'APRENDIZ', 'ACTIVO')
                """, participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                VALUES (?, ?, 'America/Lima')
                """, participanteId.value(), DIA_PROGRAMA);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra la participacion, los registros, las evidencias y el ledger.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    private RegistroHabitoId seedRegistroPendiente(String claveSistema) {
        HabitoId habitoId = HabitoId.of(jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class, claveSistema));
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        saveRegistroPort.save(RegistroHabito.generar(id, participanteId, habitoId, DIA, DIA_PROGRAMA,
                TipoDia.delDia(DIA), false, AHORA));
        return id;
    }

    private void subirTexto(RegistroHabitoId id, String texto) {
        subirEvidencia.subir(new SubirEvidenciaRegistroCommand(participanteId, id, TipoEvidencia.TEXTO, null, null,
                texto, null, null, null));
    }

    private CompletarRegistroCommand cierreGenerico(RegistroHabitoId id) {
        return new CompletarRegistroCommand(participanteId, id, null, null);
    }

    private String estadoDe(RegistroHabitoId id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class,
                id.value());
    }

    private void exigirQueNoSeCerroNiSePago(RegistroHabitoId id) {
        assertThat(estadoDe(id)).isEqualTo("PENDIENTE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Integer.class,
                participanteId.value())).isZero();
    }

    private void exigirUnSoloPago(RegistroHabitoId id) {
        assertThat(estadoDe(id)).isEqualTo("COMPLETADO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT puntos_liga FROM renaser.puntajes_participante WHERE participante_id = ?", Integer.class,
                participanteId.value())).isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Integer.class,
                participanteId.value())).isEqualTo(1);
    }

    // ─── Pastilla Renacer ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Pastilla Renacer: el ✓ del APK viejo (el cierre generico) se rechaza y no deja ni un punto")
    void pastillaPorElCierreGenericoSeRechaza() {
        RegistroHabitoId id = seedRegistroPendiente(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER);

        assertThatThrownBy(() -> completarRegistro.completar(cierreGenerico(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_PASTILLA);

        exigirQueNoSeCerroNiSePago(id);
    }

    @Test
    @DisplayName("Pastilla Renacer: ni subiendo un texto largo antes se abre el cierre generico")
    void pastillaConTextoSubidoSigueRechazada() {
        RegistroHabitoId id = seedRegistroPendiente(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER);
        subirTexto(id, DOS_RESPUESTAS);

        assertThatThrownBy(() -> completarRegistro.completar(cierreGenerico(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_PASTILLA);

        exigirQueNoSeCerroNiSePago(id);
    }

    @Test
    @DisplayName("Pastilla Renacer: por su flujo (la entrega de Espiritu) se cierra, guarda el resumen y paga una vez")
    void pastillaPorSuFlujoSeCierra() {
        RegistroHabitoId id = seedRegistroPendiente(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER);

        var resultado = transactionTemplate.execute(status ->
                completarPastillaRenacer.completarDeHoy(participanteId, DOS_RESPUESTAS).orElseThrow());

        assertThat(resultado.registroId()).isEqualTo(id.value());
        assertThat(resultado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        exigirUnSoloPago(id);
        assertThat(jdbcTemplate.queryForObject("SELECT respuesta_texto FROM renaser.registros_habito WHERE id = ?",
                String.class, id.value())).isEqualTo(DOS_RESPUESTAS);
    }

    // ─── Audioterapia semanal ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audioterapia: el ✓ del APK viejo (sin ninguna evidencia) se rechaza y no deja ni un punto")
    void audioterapiaSinEvidenciaSeRechaza() {
        RegistroHabitoId id = seedRegistroPendiente(AudioterapiaDelAprendizPort.CLAVE_SISTEMA_AUDIOTERAPIA);

        assertThatThrownBy(() -> completarRegistro.completar(cierreGenerico(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_AUDIOTERAPIA);

        exigirQueNoSeCerroNiSePago(id);
    }

    @Test
    @DisplayName("Audioterapia: un texto de menos de 30 caracteres no cuenta como las dos respuestas")
    void audioterapiaConUnTextoCortoSeRechaza() {
        RegistroHabitoId id = seedRegistroPendiente(AudioterapiaDelAprendizPort.CLAVE_SISTEMA_AUDIOTERAPIA);
        subirTexto(id, "ok");

        assertThatThrownBy(() -> completarRegistro.completar(cierreGenerico(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_AUDIOTERAPIA);

        exigirQueNoSeCerroNiSePago(id);
    }

    @Test
    @DisplayName("Audioterapia: con las dos respuestas ya subidas como evidencia de TEXTO se cierra y paga una vez")
    void audioterapiaConSusRespuestasSeCierra() {
        RegistroHabitoId id = seedRegistroPendiente(AudioterapiaDelAprendizPort.CLAVE_SISTEMA_AUDIOTERAPIA);
        subirTexto(id, DOS_RESPUESTAS);

        var completado = completarRegistro.completar(cierreGenerico(id));

        assertThat(completado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        exigirUnSoloPago(id);
    }

    @Test
    @DisplayName("Audioterapia: el texto de otro registro de la misma persona no sirve")
    void audioterapiaConElTextoDeOtroRegistroSeRechaza() {
        RegistroHabitoId id = seedRegistroPendiente(AudioterapiaDelAprendizPort.CLAVE_SISTEMA_AUDIOTERAPIA);
        RegistroHabitoId otro = seedRegistroPendiente(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER);
        subirTexto(otro, DOS_RESPUESTAS);

        assertThatThrownBy(() -> completarRegistro.completar(cierreGenerico(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_AUDIOTERAPIA);

        exigirQueNoSeCerroNiSePago(id);
    }
}
