package com.renaser.os.mentoring.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.application.ports.in.ResumirSemanaDelSemaforoUseCase;
import com.renaser.os.mentoring.domain.model.resumen.ReglasDelResumenSemanal;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta, con Postgres real: los avisos del cierre semanal del semáforo (D-168) salen,
 * pasan por el outbox de Spring Modulith y terminan en la bandeja —y, para la persona, en el chat
 * del acompañante—.
 *
 * <p><b>Es la prueba de la clase de E-250.</b> El unitario verifica que cada evento se publica dentro
 * de un {@code TransactionTemplate}; lo que solo se ve con Spring de verdad es que el
 * {@code @ApplicationModuleListener} —que corre después del commit— efectivamente se ejecuta. Un
 * evento publicado sin transacción deja su publicación incompleta en el outbox, y esta prueba falla.
 *
 * <p>Se doblan solo las fronteras que este módulo no controla: el reloj (sábado 00:40 en Lima), el
 * grupo de {@code community} y el semáforo de {@code points}. Con {@code @Primary}, así la prueba no
 * depende de que el cierre del semáforo ya exista, y sigue valiendo cuando exista. El mensaje del
 * acompañante se prende acá con una plantilla de prueba (en producción también arranca encendido,
 * desde el 2026-09-25, con las plantillas de {@code application.yaml}).
 */
@SpringBootTest(properties = {
        "renaser.ia.acompanante.semaforo-en-chat=true",
        "renaser.ia.acompanante.semaforo-en-chat-plantilla-verde=Cerraste la semana en verde, {etiqueta}: {porcentaje} %."
})
@Import({TestcontainersConfiguration.class, ResumenSemanalDelSemaforoIT.Dobles.class})
class ResumenSemanalDelSemaforoIT {

    private static final Instant SABADO_0040_LIMA = Instant.parse("2026-09-26T05:40:00Z");
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);
    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID MENTORA = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID PERSONA = UUID.randomUUID();
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    @Autowired
    private ResumirSemanaDelSemaforoUseCase resumirSemana;
    @Autowired
    private ApplicationEventPublisher eventos;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void elResumenLlegaALaBandejaDelMentorYDelAdministradorUnaSolaVez() throws InterruptedException {
        insertarUsuario(MENTORA, "MENTOR");
        insertarUsuario(ADMIN, "ADMIN");
        // Ana tiene cuenta y está activa: el padrón del semáforo solo cuenta cuentas activas (2026-09-25).
        insertarUsuario(ANA.value(), "APRENDIZ");

        assertThat(resumirSemana.resumir()).isEqualTo(1);
        esperarQueElOutboxTermine();

        assertThat(notificacionesDe(MENTORA)).singleElement().satisfies(fila -> assertThat(fila)
                .containsEntry("tipo", "RESUMEN_SEMANAL")
                .containsEntry("titulo", "Tu grupo cerró la semana")
                // Ana cerró en verde y es la única: el caso es "nadie necesita apoyo extra", sin cifras.
                .containsEntry("cuerpo", "En Grupo Fénix, nadie necesita apoyo extra esta semana. ¡Buen acompañamiento!")
                .containsEntry("ruta_app", "/mentor/groups/" + GRUPO + "/semaforo")
                .containsEntry("origen_evento_id", ReglasDelResumenSemanal.claveDelGrupo(GRUPO, VIERNES)));
        assertThat(notificacionesDe(ADMIN)).singleElement().satisfies(fila -> assertThat(fila)
                .containsEntry("tipo", "RESUMEN_SEMANAL")
                .containsEntry("titulo", "Semana cerrada")
                .containsEntry("ruta_app", "/semaforo/grupos")
                .containsEntry("origen_evento_id", ReglasDelResumenSemanal.claveGeneral(VIERNES)));

        // La corrida de la hora siguiente vuelve a publicar con las mismas claves: la base no duplica.
        resumirSemana.resumir();
        esperarQueElOutboxTermine();
        assertThat(notificacionesDe(MENTORA)).hasSize(1);
        assertThat(notificacionesDe(ADMIN)).hasSize(1);
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void elCierreDeLaPersonaLlegaSinCifrasYElAcompananteLoCuentaUnaSolaVez() throws InterruptedException {
        insertarUsuario(PERSONA, "APRENDIZ");
        UUID clave = SemanaDelSemaforoCerradaEvent.claveDe(PERSONA, VIERNES);
        var cierre = new SemanaDelSemaforoCerradaEvent(clave, PERSONA, VIERNES.minusDays(6), VIERNES,
                new BigDecimal("86.0"), ColorSemaforo.VERDE, 7, Instant.parse("2026-09-26T05:25:03Z"));

        publicarEnTransaccion(cierre);
        esperarQueElOutboxTermine();
        publicarEnTransaccion(cierre); // una reentrega del mismo cierre
        esperarQueElOutboxTermine();

        assertThat(notificacionesDe(PERSONA)).singleElement().satisfies(fila -> assertThat(fila)
                .containsEntry("tipo", "RESUMEN_SEMANAL")
                .containsEntry("titulo", "Tu semana ya cerró")
                .containsEntry("cuerpo", "Mira cómo te fue en tu semáforo de la semana.")
                .containsEntry("ruta_app", "/semaforo")
                .containsEntry("origen_evento_id", clave));
        assertThat(jdbcTemplate.queryForList("SELECT contenido FROM renaser.mensajes_renasia WHERE usuario_id = ?",
                String.class, PERSONA)).containsExactly("Cerraste la semana en verde, Al día: 86 %.");
    }

    private void publicarEnTransaccion(Object evento) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> eventos.publishEvent(evento));
    }

    private void insertarUsuario(UUID id, String rol) {
        jdbcTemplate.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) "
                        + "VALUES (?, ?, 'Persona de Prueba', CAST(? AS renaser.rol_usuario))",
                id, rol.toLowerCase() + "-" + id + "@renaser.com", rol);
    }

    private List<Map<String, Object>> notificacionesDe(UUID usuarioId) {
        return jdbcTemplate.queryForList("SELECT tipo::text AS tipo, titulo, cuerpo, ruta_app, origen_evento_id "
                + "FROM renaser.notificaciones WHERE usuario_id = ?", usuarioId);
    }

    /**
     * Poll corto (sin Awaitility en el classpath): los listeners corren async después del commit. Una
     * publicación sin {@code completion_date} es un listener que todavía no terminó —o que nunca va a
     * correr, como en E-250—.
     */
    private void esperarQueElOutboxTermine() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (pendientesEnElOutbox() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(pendientesEnElOutbox()).as("publicaciones sin completar en el outbox").isZero();
    }

    /** Sin esquema a propósito: V2 crea la tabla en el esquema por defecto de la conexión, no en renaser. */
    private int pendientesEnElOutbox() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
    }

    @TestConfiguration
    static class Dobles {

        @Bean
        @Primary
        Clock sabadoALas0040EnLima() {
            return FixedClock.at(SABADO_0040_LIMA);
        }

        @Bean
        @Primary
        AcompanamientoFinder grupoFenixConAna() {
            return new GrupoFenixConAna();
        }

        @Bean
        @Primary
        SemaforoFinder anaCerroEnVerde() {
            return new AnaCerroEnVerde();
        }
    }

    /** Un grupo de Lima con su mentora y una aprendiz. Lo demás responde vacío. */
    static final class GrupoFenixConAna implements AcompanamientoFinder {

        @Override
        public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            return List.of(new GrupoAcompanado(GRUPO, "Grupo Fénix", UserId.of(MENTORA), UUID.randomUUID(),
                    "America/Lima", 3));
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return GRUPO.equals(grupoId) ? List.of(ANA) : List.of();
        }

        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return false;
        }

        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return false;
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return Optional.empty();
        }
    }

    /** Ana cerró la semana en verde; nadie más se mide. */
    static final class AnaCerroEnVerde implements SemaforoFinder {

        @Override
        public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
            return participantes.contains(ANA)
                    ? Map.of(ANA, new VentanaDelSemaforo(semanaHasta.minusDays(6), semanaHasta,
                            new BigDecimal("86.0"), ColorSemaforo.VERDE, 7, true, List.of()))
                    : Map.of();
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
            return Map.of();
        }

        @Override
        public DetalleDelSemaforo detalleDe(UserId participante, int semanas) {
            return DetalleDelSemaforo.noAplica(ZoneId.of("America/Lima"), true);
        }
    }
}
