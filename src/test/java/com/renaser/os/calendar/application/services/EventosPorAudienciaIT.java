package com.renaser.os.calendar.application.services;

import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase;
import com.renaser.os.calendar.application.ports.in.audiencia.PedidoDeAudiencia;
import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventosConMeetUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La spec 044 de punta a punta contra Postgres de verdad, con los adaptadores reales (grupos de {@code community}, cuentas y formaciones de
 * {@code users}, el semáforo de {@code points}): quién crea, quién ve, a quién le llegan los recordatorios y qué pasa con «No voy».
 *
 * <p>El programa: un mentor con su grupo (dos aprendices en rojo y uno en verde), otro mentor con su grupo (uno en rojo), una líder, una
 * administradora y una alquimista.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventosPorAudienciaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private EventosConMeetUseCase eventos;
    @Autowired
    private ObtenerEventoUseCase obtener;
    @Autowired
    private ListarEventosParaVisorUseCase listar;
    @Autowired
    private GenerarRecordatoriosUseCase generar;
    @Autowired
    private DespacharRecordatoriosUseCase despachar;
    @Autowired
    private ConfirmarAsistenciaUseCase confirmar;
    @Autowired
    private ConvocatoriaUseCase convocatoria;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID cohorte;
    private UserId admin;
    private UserId alquimista;
    private UserId lider;
    private UserId mentor;
    private UserId otroMentor;
    private UserId rojo1;
    private UserId rojo2;
    private UserId verde;
    private UserId rojoDeOtroGrupo;
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private final List<UUID> creados = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        grupos.clear();
        creados.clear();
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte 044', CURRENT_DATE - 10)", cohorte);
        admin = usuario("ADMIN");
        alquimista = usuario("ALQUIMISTA");
        lider = usuario("LIDER_MENTORES");
        mentor = conPerfil(usuario("MENTOR"));
        otroMentor = conPerfil(usuario("MENTOR"));
        UUID grupo = grupo(mentor);
        UUID otroGrupo = grupo(otroMentor);
        rojo1 = aprendizCon(grupo, 2);
        rojo2 = aprendizCon(grupo, 3);
        verde = aprendizCon(grupo, 10);
        rojoDeOtroGrupo = aprendizCon(otroGrupo, 1);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'eventos.convocar'");
        copiaDeEstados.recargar();
        creados.forEach(id -> jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.eventos WHERE creado_por IN (?, ?, ?, ?, ?)", admin.value(), alquimista.value(), lider.value(),
                mentor.value(), otroMentor.value());
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.semaforo_dias WHERE participante_id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.asignaciones_celula WHERE celula_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", id));
        grupos.forEach(id -> jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // spec 044 FR-001, FR-013, FR-014, FR-020, SC-001
    @Test
    @DisplayName("el mentor crea una sesión para sus alumnos: la ven ellos y él; el de otro grupo no la ve ni recibe avisos")
    void elMentorConSusAlumnos() {
        EventoId id = crear(mentor, TipoEvento.SESION_ESPECIAL, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS)));

        assertThat(lista(rojo1)).contains(id.value());
        assertThat(lista(verde)).contains(id.value());
        assertThat(lista(mentor)).contains(id.value());
        assertThat(lista(rojoDeOtroGrupo)).doesNotContain(id.value());
        assertThatThrownBy(() -> obtener.obtener(rojoDeOtroGrupo, id)).isInstanceOf(NotAuthorizedException.class);

        generar.generar(Instant.now());
        assertThat(avisos(id, rojo1)).isPositive();
        assertThat(avisos(id, mentor)).as("quien lo creó, como organizador").isPositive();
        assertThat(avisos(id, rojoDeOtroGrupo)).isZero();
    }

    // spec 044 FR-009, FR-010, FR-013, FR-020, SC-001, SC-002
    @Test
    @DisplayName("la líder convoca a los rojos y a un mentor por su nombre: llega a esos y a nadie más; el verde no lo ve")
    void laLiderConLosRojosYUnaPersona() {
        List<ParteDeAudiencia> partes = List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO), ParteDeAudiencia.persona(otroMentor.value()));
        int llega = convocatoria.vistaPrevia(lider, null, partes).total();
        EventoId id = crear(lider, TipoEvento.SESION_PERSONALIZADA, partes);

        assertThat(llega).isEqualTo(4);
        assertThat(lista(rojo1)).contains(id.value());
        assertThat(lista(rojoDeOtroGrupo)).contains(id.value());
        assertThat(lista(otroMentor)).contains(id.value());
        assertThat(lista(verde)).doesNotContain(id.value());
        assertThat(lista(mentor)).as("un mentor no elegido no lo ve").doesNotContain(id.value());

        generar.generar(Instant.now());
        assertThat(destinatarios(id)).as("SC-002: los de «Llega a N personas» más la líder").hasSize(llega + 1)
                .contains(rojo1.value(), rojo2.value(), rojoDeOtroGrupo.value(), otroMentor.value(), lider.value());
    }

    // spec 044 FR-002, FR-020
    @Test
    @DisplayName("la líder agenda con la Alquimista: la ven las dos; a Administración no le llega ningún aviso")
    void laLiderConLaAlquimista() {
        EventoId id = crear(lider, TipoEvento.SESION_PERSONALIZADA, List.of(new ParteDeAudiencia(TipoDeParte.PERSONAL, alquimista.value(), null)));

        assertThat(lista(alquimista)).contains(id.value());
        generar.generar(Instant.now());
        assertThat(destinatarios(id)).containsExactlyInAnyOrder(alquimista.value(), lider.value());
    }

    // spec 044 FR-004, SC-003
    @Test
    @DisplayName("SC-003: un mentor que invita a quien no es su alumno recibe 403 y no se guarda nada")
    void mentorFueraDeSuAlcance() {
        int antes = eventosDe(mentor);

        assertThatThrownBy(() -> crear(mentor, TipoEvento.SESION_ESPECIAL, List.of(ParteDeAudiencia.persona(rojoDeOtroGrupo.value()))))
                .isInstanceOfSatisfying(AudienciaRechazadaException.class, e -> assertThat(e.ids()).containsExactly(rojoDeOtroGrupo.value()));
        assertThat(eventosDe(mentor)).isEqualTo(antes);
    }

    // spec 044 FR-007
    @Test
    @DisplayName("FR-007: el mentor no crea una Mentoría del Alquimista ni un evento para todos")
    void elMentorNoCreaOtrosTipos() {
        assertThatThrownBy(() -> crear(mentor, TipoEvento.MENTORIA_ALQUIMISTA, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS))))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> eventos.crear(comando(mentor, TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // spec 053 FR-001
    @Test
    @DisplayName("spec 053 FR-001: el líder crea su «Sesión personalizada» y ya no la especial ni la espontánea; el mentor, la personalizada no")
    void elLiderSoloCreaLaSesionPersonalizada() {
        List<ParteDeAudiencia> partes = List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO));

        assertThat(obtener.obtener(lider, crear(lider, TipoEvento.SESION_PERSONALIZADA, partes)).evento().tipoEvento())
                .isEqualTo(TipoEvento.SESION_PERSONALIZADA);
        assertThatThrownBy(() -> crear(lider, TipoEvento.SESION_ESPECIAL, partes)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> crear(lider, TipoEvento.ESPONTANEO, partes)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> crear(mentor, TipoEvento.SESION_PERSONALIZADA, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // spec 044 FR-006; spec 053 FR-002 (los eventos de «Sesión especial» de antes siguen editables por el líder)
    @Test
    @DisplayName("FR-006 (P7): la líder edita y cancela el evento de un mentor; el mentor no toca el de Administración")
    void quienGestiona() {
        EventoId delMentor = crear(mentor, TipoEvento.SESION_ESPECIAL, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS)));
        EventoId deAdmin = eventos.crear(comando(admin, TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, null)).evento().evento().id();
        creados.add(deAdmin.value());

        eventos.actualizar(edicion(lider, delMentor, "Repaso movido", null));
        assertThat(obtener.obtener(lider, delMentor).evento().titulo()).isEqualTo("Repaso movido");
        assertThatThrownBy(() -> eventos.eliminar(mentor, deAdmin)).isInstanceOf(NotAuthorizedException.class);
        eventos.eliminar(lider, delMentor);
        assertThat(eventosDe(mentor)).isZero();
    }

    // spec 044 FR-030, SC-005
    @Test
    @DisplayName("SC-005: editar sin mandar la audiencia (una app anterior) la conserva")
    void editarSinAudienciaLaConserva() {
        EventoId id = crear(lider, TipoEvento.SESION_PERSONALIZADA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        int antes = invitados(id);

        eventos.actualizar(edicion(lider, id, "Otro nombre", null));

        assertThat(invitados(id)).isEqualTo(antes).isEqualTo(3);
        assertThat(lista(verde)).doesNotContain(id.value());
    }

    // spec 044 FR-022
    @Test
    @DisplayName("FR-022: quien dijo «No voy» no recibe los recordatorios; quien no respondió, sí")
    void noVoyNoRecibe() {
        EventoId id = crear(lider, TipoEvento.SESION_PERSONALIZADA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        Instant inicio = inicioDe(id);
        confirmar.confirmar(rojo1, id, inicio, EstadoConfirmacion.NO_ASISTE);

        generar.generar(Instant.now());
        despachar.despachar(inicio.minusSeconds(60));

        assertThat(cancelados(id, rojo1, "dijo_no_voy")).isPositive();
        assertThat(enviados(id, rojo1)).isZero();
        assertThat(enviados(id, rojo2)).isPositive();
    }

    // spec 044 FR-028, SC-006
    @Test
    @DisplayName("SC-006: con eventos.convocar apagado el mentor no crea, y Administración crea como siempre")
    void interruptorApagado() {
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('eventos.convocar', false, nextval('renaser.funcionalidades_version_seq'), ?, now())", admin.value());
        copiaDeEstados.recargar();

        assertThatThrownBy(() -> crear(mentor, TipoEvento.SESION_ESPECIAL, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS))))
                .isInstanceOf(FuncionalidadApagadaException.class);
        EventoId deAdmin = eventos.crear(comando(admin, TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, null)).evento().evento().id();
        creados.add(deAdmin.value());
        assertThat(lista(verde)).contains(deAdmin.value());
    }

    // spec 044 FR-016, FR-018, FR-019
    @Test
    @DisplayName("FR-016/018/019: «Para quién es» solo para quien lo creó y Administración; el invitado ve solo quién lo organizó")
    void quienVeLaAudiencia() {
        EventoId id = crear(lider, TipoEvento.SESION_PERSONALIZADA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        Evento evento = obtener.obtener(lider, id).evento();

        assertThat(convocatoria.resumenPara(lider, evento)).get().satisfies(r -> assertThat(r.total()).isEqualTo(3));
        assertThat(convocatoria.resumenPara(admin, evento)).isPresent();
        assertThat(convocatoria.resumenPara(rojo1, evento)).isEmpty();
        assertThat(convocatoria.organizador(evento)).get().satisfies(o -> assertThat(o.rol()).isEqualTo("Líder de mentores"));
    }

    // spec 044 FR-008, FR-031
    @Test
    @DisplayName("FR-008/FR-031: el evento del mentor nace en su formación; los eventos para todos siguen llegando a todos")
    void formacionYEventosDeSiempre() {
        EventoId delMentor = crear(mentor, TipoEvento.ESPONTANEO, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS)));
        EventoId paraTodos = eventos.crear(comando(admin, TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, null)).evento().evento().id();
        creados.add(paraTodos.value());

        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.eventos WHERE id = ?", String.class, delMentor.value())).isEqualTo("ESCUELA");
        assertThat(lista(rojoDeOtroGrupo)).contains(paraTodos.value());
        assertThat(lista(verde)).contains(paraTodos.value());
    }

    // spec 044 FR-025
    @Test
    @DisplayName("FR-025: cuándo se avisa no cambia: el invitado recibe los mismos avisos que en un evento para todos del mismo tipo (spec 053: el de la sesión personalizada)")
    void lasReglasNoCambian() {
        EventoId elegida = crear(lider, TipoEvento.SESION_PERSONALIZADA, List.of(ParteDeAudiencia.persona(rojo1.value())));
        EventoId paraTodos = eventos.crear(comando(admin, TipoEvento.SESION_PERSONALIZADA, TipoAudiencia.TODOS, null)).evento().evento().id();
        creados.add(paraTodos.value());

        generar.generar(Instant.now());

        assertThat(avisos(elegida, rojo1)).isPositive().isEqualTo(avisos(paraTodos, rojo1));
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private EventoId crear(UserId quien, TipoEvento tipo, List<ParteDeAudiencia> partes) {
        EventoId id = eventos.crear(comando(quien, tipo, TipoAudiencia.SELECCION, new PedidoDeAudiencia(partes, false))).evento().evento().id();
        creados.add(id.value());
        return id;
    }

    private CrearEventoCommand comando(UserId quien, TipoEvento tipo, TipoAudiencia audiencia, PedidoDeAudiencia pedido) {
        Instant inicio = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        com.renaser.os.shared.domain.Formacion formacion = quien.equals(admin) || quien.equals(alquimista)
                ? com.renaser.os.shared.domain.Formacion.ESCUELA : null;
        return new CrearEventoCommand(quien, "Sesión 044", null, inicio, 60, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij",
                audiencia, null, null, null, tipo, false, false, false, null, Set.of(), List.of(), formacion, null, pedido);
    }

    private ActualizarEventoCommand edicion(UserId quien, EventoId id, String titulo, PedidoDeAudiencia pedido) {
        Evento actual = obtener.obtener(quien, id).evento();
        return new ActualizarEventoCommand(quien, id, titulo, null, actual.iniciaEn(), 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc-defg-hij", TipoAudiencia.SELECCION, null, null, null, false, false, false, null, Set.of(),
                List.of(), null, pedido);
    }

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))",
                id, "e044-" + id + "@renaser.test", "Persona " + rol, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId conPerfil(UserId mentorId) {
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mentorId.value());
        return mentorId;
    }

    private UUID grupo(UserId mentorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id) VALUES (?, ?, ?, ?)",
                id, "Grupo " + id.toString().substring(0, 6), mentorId.value(), cohorte);
        grupos.add(id);
        asignar(id, mentorId, "MENTOR");
        return id;
    }

    /** Aprendiz inscrito, con su programa activado y siete días cerrados con {@code cumplidos} de 10: 2 o 3 es rojo, 10 es verde. */
    private UserId aprendizCon(UUID grupo, int cumplidos) {
        UserId aprendiz = usuario("APRENDIZ");
        LocalDate hoy = LocalDate.now(LIMA);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id, fecha_inicio, programa_activado_en)
                VALUES (?, 10, 'America/Lima', ?, ?, now())
                """, aprendiz.value(), grupo, hoy.minusDays(9));
        asignar(grupo, aprendiz, "APRENDIZ");
        for (int atras = 1; atras <= 7; atras++) {
            jdbc.update("""
                    INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos,
                                                       objetivos_programados, objetivos_cumplidos, calculado_en)
                    VALUES (?, ?, 10, ?, 0, 0, now())
                    """, aprendiz.value(), hoy.minusDays(atras), cumplidos);
        }
        return aprendiz;
    }

    private void asignar(UUID grupo, UserId usuario, String funcion) {
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 day', 'ADMINISTRATIVO', ?)
                """, grupo, usuario.value(), funcion, "prueba-" + UUID.randomUUID());
    }

    private List<UUID> lista(UserId visor) {
        Instant ahora = Instant.now();
        return listar.listar(visor, ahora, ahora.plus(Duration.ofDays(3))).stream().map(o -> o.evento().id().value()).toList();
    }

    private Instant inicioDe(EventoId id) {
        return jdbc.queryForObject("SELECT inicia_en FROM renaser.eventos WHERE id = ?", java.time.OffsetDateTime.class, id.value()).toInstant();
    }

    private int avisos(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ?",
                Integer.class, evento.value(), usuario.value());
    }

    private List<UUID> destinatarios(EventoId evento) {
        return jdbc.queryForList("SELECT DISTINCT usuario_id FROM renaser.recordatorios_evento WHERE evento_id = ?", UUID.class, evento.value());
    }

    private int cancelados(EventoId evento, UserId usuario, String motivo) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ? AND motivo_cancelacion = ?",
                Integer.class, evento.value(), usuario.value(), motivo);
    }

    private int enviados(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ? AND enviado_en IS NOT NULL "
                + "AND motivo_cancelacion IS NULL", Integer.class, evento.value(), usuario.value());
    }

    private int invitados(EventoId evento) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.invitados_evento WHERE evento_id = ?", Integer.class, evento.value());
    }

    private int eventosDe(UserId quien) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.eventos WHERE creado_por = ?", Integer.class, quien.value());
    }
}
