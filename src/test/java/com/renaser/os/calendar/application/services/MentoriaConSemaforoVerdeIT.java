package com.renaser.os.calendar.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.calendar.application.ports.in.asistencia.PersonaConvocada;
import com.renaser.os.calendar.application.ports.in.asistencia.VerRespuestasDelEventoUseCase;
import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-293 (cierra D-213): la Mentoria del Alquimista la ve un aprendiz solo con el semaforo en VERDE. Contra Postgres de
 * verdad, con el semaforo real de {@code points} (sus dias sembrados en {@code semaforo_dias}, lo que deja el barrido)
 * y dos aprendices —uno en verde y otro en amarillo— mas el personal.
 *
 * <p>Contra el adaptador anterior ({@code ElegibilidadSegunAudienciaAdapter}, que dejaba pasar a toda la audiencia)
 * fallan todos los casos en que el aprendiz no esta en verde. El reloj es una madrugada UTC (03:00Z = 22:00 del dia
 * anterior en Lima, regla 03) del 2027, para que ningun planificador de otro contexto de la suite, que lee la hora de
 * verdad, vea vencidos los avisos de aca. Sin {@code @Transactional}: los casos de uso abren la suya y los avisos
 * se entregan despues del commit.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MentoriaConSemaforoVerdeIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant T0 = Instant.parse("2027-03-10T03:00:00Z");
    /** El 9 de marzo en Lima: el dia local de la persona mientras el reloj marca el 10 en UTC. */
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2027, 3, 9);
    /** Una hora despues del reloj: el aviso de «10 min antes» queda a las 03:50Z. */
    private static final Instant INICIO = T0.plus(Duration.ofHours(1));
    private static final Duration LUEGO_DEL_AVISO = Duration.ofMinutes(55);
    private static final String NO_ELEGIBLE = "dejo_de_ser_elegible";

    @Autowired
    private RelojDePrueba clock;
    @Autowired
    private CrearEventoUseCase crear;
    @Autowired
    private ObtenerEventoUseCase obtener;
    @Autowired
    private ListarEventosParaVisorUseCase listar;
    @Autowired
    private ConfirmarAsistenciaUseCase confirmar;
    @Autowired
    private GenerarRecordatoriosUseCase generar;
    @Autowired
    private DespacharRecordatoriosUseCase despachar;
    @Autowired
    private VerRespuestasDelEventoUseCase respuestas;
    @Autowired
    private ProximoEventoFinder proximoEvento;
    @Autowired
    private EventosDelParticipanteFinder agenda;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> eventos = new ArrayList<>();
    private UserId admin;
    private UserId mentor;
    private UserId verde;
    private UserId amarillo;

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        eventos.clear();
        clock.fijar(T0);
        admin = usuario("ADMIN");
        mentor = usuario("MENTOR");
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mentor.value());
        verde = aprendiz(10);
        amarillo = aprendiz(7);
    }

    @AfterEach
    void limpiar() {
        eventos.forEach(id -> jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("D-293: el verde la ve en la lista, la agenda, «Proximo evento» y el detalle, y responde «Voy»; "
            + "el amarillo no la ve en ninguna parte; el mentor, si")
    void soloElVerdeLaVeEnTodosLosCaminos() {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);

        assertThat(idsEnLaListaDe(verde)).contains(mentoria.value());
        assertThat(obtener.obtener(verde, mentoria).evento().id()).isEqualTo(mentoria);
        assertThat(proximoEvento.proximoEventoDe(verde)).get().extracting(ProximoEventoFinder.ProximoEvento::eventoId)
                .isEqualTo(mentoria.value());
        assertThat(agenda.proximosDias(verde, 7).eventos()).extracting(EventosDelParticipanteFinder.EventoDeLaAgenda::eventoId)
                .contains(mentoria.value());
        confirmar.confirmar(verde, mentoria, INICIO, EstadoConfirmacion.ASISTE);

        assertThat(idsEnLaListaDe(amarillo)).doesNotContain(mentoria.value());
        assertThat(proximoEvento.proximoEventoDe(amarillo)).isEmpty();
        assertThat(agenda.proximosDias(amarillo, 7).eventos()).isEmpty();
        assertThatThrownBy(() -> confirmar.confirmar(amarillo, mentoria, INICIO, EstadoConfirmacion.ASISTE))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(idsEnLaListaDe(mentor)).contains(mentoria.value());
        assertThat(obtener.obtener(mentor, mentoria).evento().id()).isEqualTo(mentoria);
    }

    @Test
    @DisplayName("D-293: sin verde da lo mismo: rojo, sin datos y sin semaforo; el detalle responde igual que a "
            + "cualquier evento que no es para esa persona")
    void rojoSinDatosYSinSemaforoTampocoLaVen() {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);
        EventoId deOtros = evento(TipoEvento.SESION_ESPECIAL, TipoAudiencia.ROLES, Set.of(RolUsuario.ADMIN), false);
        UserId rojo = aprendiz(3);
        UserId sinDatos = aprendizActivado();
        UserId sinSemaforo = usuario("APRENDIZ");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone) VALUES (?, 2, 'America/Lima')",
                sinSemaforo.value());

        for (UserId persona : List.of(amarillo, rojo, sinDatos, sinSemaforo)) {
            assertThat(idsEnLaListaDe(persona)).doesNotContain(mentoria.value());
            assertThatThrownBy(() -> obtener.obtener(persona, mentoria)).isInstanceOf(NotAuthorizedException.class)
                    .hasMessage("No tienes acceso a este evento");
        }
        assertThatThrownBy(() -> obtener.obtener(amarillo, deOtros)).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("No tienes acceso a este evento");
    }

    @Test
    @DisplayName("D-293: el personal no cambia — guia, mentor, lider, admin, alquimista y Soporte TI la abren sin semaforo")
    void elPersonalNoCambia() {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);

        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA", "SOPORTE_TI")) {
            UserId persona = usuario(rol);
            assertThat(obtener.obtener(persona, mentoria).evento().id()).as(rol).isEqualTo(mentoria);
            assertThat(idsEnLaListaDe(persona)).as(rol).contains(mentoria.value());
        }
    }

    @Test
    @DisplayName("D-293: los demas tipos de evento no cambian: el amarillo ve una sesion especial para todos")
    void losDemasTiposNoCambian() {
        EventoId sesion = evento(TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, Set.of(), false);

        assertThat(idsEnLaListaDe(amarillo)).contains(sesion.value());
        assertThat(obtener.obtener(amarillo, sesion).evento().id()).isEqualTo(sesion);
    }

    @Test
    @DisplayName("D-293: «Avisar a todos al guardarlo» y los recordatorios llegan solo a quien es elegible, y el aviso "
            + "de ahora le llega al verde y no al amarillo")
    void losAvisosLleganSoloAlElegible() throws InterruptedException {
        EventoId mentoria = mentoria(TipoAudiencia.ROLES, true, Set.of(RolUsuario.TRAINEE, RolUsuario.MENTOR));

        generar.generar(T0);

        assertThat(recordatoriosDe(mentoria, verde)).as("el anuncio y el de 10 min antes").isEqualTo(2);
        assertThat(recordatoriosDe(mentoria, mentor)).as("el personal sigue recibiendo los suyos").isEqualTo(2);
        assertThat(recordatoriosDe(mentoria, amarillo)).isZero();

        despachar.despachar(T0);
        esperar("la notificacion del anuncio al verde y al mentor",
                () -> notificacionesDe(verde) >= 1 && notificacionesDe(mentor) >= 1);
        assertThat(notificacionesDe(amarillo)).isZero();
    }

    @Test
    @DisplayName("D-293: si el color cambia manda el de ahora — pasar a verde la muestra; dejar de estarlo la esconde, "
            + "conserva su «Voy» y «Quien respondio» lo sigue mostrando")
    void alCambiarElColorManda_elDeAhora() {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);
        UserId rojo = aprendiz(3);
        confirmar.confirmar(verde, mentoria, INICIO, EstadoConfirmacion.ASISTE);

        ponerSemaforo(amarillo, 10);
        ponerSemaforo(verde, 7);

        assertThat(idsEnLaListaDe(amarillo)).as("al pasar a verde").contains(mentoria.value());
        assertThat(obtener.obtener(amarillo, mentoria).evento().id()).isEqualTo(mentoria);
        assertThat(idsEnLaListaDe(verde)).as("al dejar de estar en verde").doesNotContain(mentoria.value());
        assertThatThrownBy(() -> obtener.obtener(verde, mentoria)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> confirmar.confirmar(verde, mentoria, INICIO, EstadoConfirmacion.NO_ASISTE))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.confirmaciones_evento WHERE evento_id = ? AND usuario_id = ?",
                String.class, mentoria.value(), verde.value())).as("su «Voy» no se borra").isEqualTo("ASISTE");
        List<UserId> enLaHoja = respuestas.respuestas(admin, mentoria, INICIO).personas().stream()
                .map(PersonaConvocada::id).toList();
        assertThat(enLaHoja).as("quien respondio sigue en la hoja del D-256, aunque ya no sea elegible")
                .contains(verde, amarillo).as("y quien no es elegible ni respondio, no").doesNotContain(rojo);
    }

    @Test
    @DisplayName("D-293: un recordatorio ya generado se evalua al enviarlo — a quien dejo de estar en verde se le "
            + "cancela con dejo_de_ser_elegible; si vuelve a verde antes de la hora, sale")
    void elRecordatorioYaGeneradoSeEvaluaAlEnviar() throws InterruptedException {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);
        ponerSemaforo(amarillo, 10);
        generar.generar(T0);
        assertThat(recordatoriosDe(mentoria, verde)).isEqualTo(1);
        assertThat(recordatoriosDe(mentoria, amarillo)).isEqualTo(1);

        ponerSemaforo(amarillo, 7);
        clock.fijar(T0.plus(LUEGO_DEL_AVISO));
        despachar.despachar(clock.now());

        esperar("el aviso del verde", () -> notificacionesDe(verde) >= 1);
        assertThat(estadoDelRecordatorio(mentoria, verde)).isEqualTo("enviado");
        assertThat(estadoDelRecordatorio(mentoria, amarillo)).isEqualTo(NO_ELEGIBLE);
        assertThat(notificacionesDe(amarillo)).isZero();
    }

    @Test
    @DisplayName("D-293: quien vuelve a estar en verde antes de la hora de su aviso lo recibe: no se guardo ninguna decision")
    void quienVuelveAVerdeAntesDeLaHoraLoRecibe() throws InterruptedException {
        EventoId mentoria = mentoria(TipoAudiencia.TODOS, false);
        generar.generar(T0);
        ponerSemaforo(verde, 7);
        assertThat(idsEnLaListaDe(verde)).doesNotContain(mentoria.value());

        ponerSemaforo(verde, 10);
        clock.fijar(T0.plus(LUEGO_DEL_AVISO));
        despachar.despachar(clock.now());

        esperar("el aviso del verde", () -> notificacionesDe(verde) >= 1);
        assertThat(estadoDelRecordatorio(mentoria, verde)).isEqualTo("enviado");
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private EventoId mentoria(TipoAudiencia audiencia, boolean avisarAlGuardar) {
        return mentoria(audiencia, avisarAlGuardar, Set.of());
    }

    private EventoId mentoria(TipoAudiencia audiencia, boolean avisarAlGuardar, Set<RolUsuario> roles) {
        return evento(TipoEvento.MENTORIA_ALQUIMISTA, audiencia, roles, avisarAlGuardar);
    }

    private EventoId evento(TipoEvento tipo, TipoAudiencia audiencia, Set<RolUsuario> roles, boolean avisarAlGuardar) {
        EventoId id = crear.crear(new CrearEventoCommand(admin, "Mentoria " + UUID.randomUUID().toString().substring(0, 6),
                null, INICIO, 60, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij", audiencia, null,
                null, null, tipo, avisarAlGuardar, false, false, null, roles, List.of())).evento().id();
        eventos.add(id.value());
        return id;
    }

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, "mentoria020-" + id + "@renaser.test", "Persona " + rol, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    /** Aprendiz con el programa activado y siete dias cerrados de 10 habitos, {@code cumplidos} cumplidos cada dia. */
    private UserId aprendiz(int cumplidos) {
        UserId persona = aprendizActivado();
        for (LocalDate dia = HOY_EN_LIMA.minusDays(7); dia.isBefore(HOY_EN_LIMA); dia = dia.plusDays(1)) {
            jdbc.update("""
                    INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos,
                                                       objetivos_programados, objetivos_cumplidos, calculado_en)
                    VALUES (?, ?, 10, ?, 0, 0, ?)
                    """, persona.value(), dia, cumplidos, Timestamp.from(T0));
        }
        return persona;
    }

    /** Dia 37 de su programa el 9 de marzo, ya activado: el semaforo lo mide, pero sin ningun dia guardado. */
    private UserId aprendizActivado() {
        UserId persona = usuario("APRENDIZ");
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en,
                                                            timezone)
                VALUES (?, 37, ?, ?, 'America/Lima')
                """, persona.value(), HOY_EN_LIMA.minusDays(36), Timestamp.from(T0.minus(Duration.ofDays(40))));
        return persona;
    }

    /** 10 de 10 = verde (100 %), 7 de 10 = amarillo (70 %), 3 de 10 = rojo. */
    private void ponerSemaforo(UserId persona, int cumplidos) {
        jdbc.update("UPDATE renaser.semaforo_dias SET habitos_cumplidos = ? WHERE participante_id = ?", cumplidos,
                persona.value());
    }

    private List<UUID> idsEnLaListaDe(UserId visor) {
        return listar.listar(visor, T0, T0.plus(Duration.ofDays(3))).stream()
                .map(ocurrencia -> ocurrencia.evento().id().value()).toList();
    }

    private int recordatoriosDe(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ?",
                Integer.class, evento.value(), usuario.value());
    }

    /** El del aviso de «10 min antes»: {@code enviado}, el motivo con que se cancelo, o {@code pendiente}. */
    private String estadoDelRecordatorio(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN enviado_en IS NOT NULL THEN 'enviado' WHEN motivo_cancelacion IS NOT NULL
                            THEN motivo_cancelacion ELSE 'pendiente' END
                FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ? AND enviar_en = ?
                """, String.class, evento.value(), usuario.value(), Timestamp.from(INICIO.minus(Duration.ofMinutes(10))));
    }

    private long notificacionesDe(UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? AND tipo = 'RECORDATORIO_EVENTO'",
                Long.class, usuario.value());
    }

    /** Los avisos se entregan despues del commit, en otro hilo: se espera a que lleguen antes de limpiar. */
    private static void esperar(String que, BooleanSupplier condicion) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (!condicion.getAsBoolean() && Instant.now().isBefore(limite)) {
            Thread.sleep(250);
        }
        assertThat(condicion.getAsBoolean()).as(que).isTrue();
    }
}
