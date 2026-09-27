package com.renaser.os.calendar.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quien ve un evento y quien recibe sus avisos, contra Postgres de verdad y con los adaptadores reales
 * (asignaciones de {@code community}, cuentas de {@code users}). Tres hallazgos de la prueba de punta a
 * punta del 2026-09-27, y los tres fallan contra el codigo anterior:
 * <ul>
 *   <li>HALLAZGO-A2 (E-362): una Mentoria del Alquimista "para todos" no le llegaba a ningun aprendiz;</li>
 *   <li>E-363: el mentor de un grupo recibia el aviso del evento del grupo pero le daba 403, y los integrantes
 *       adicionales (D-139, D-141) tampoco tenian acceso;</li>
 *   <li>SEG-13 (E-364): el servidor guardaba un link {@code javascript:}.</li>
 * </ul>
 *
 * <p>Sin {@code @Transactional} de clase: los casos de uso abren la suya.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventosDeGrupoYMentoriaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private CrearEventoUseCase crear;
    @Autowired
    private ObtenerEventoUseCase obtener;
    @Autowired
    private ListarEventosParaVisorUseCase listar;
    @Autowired
    private GenerarRecordatoriosUseCase generar;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID cohorte;
    private UserId admin;
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private final List<UUID> eventos = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        grupos.clear();
        eventos.clear();
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte eventos', CURRENT_DATE - 10)",
                cohorte);
        admin = usuario("ADMIN");
    }

    @AfterEach
    void limpiar() {
        eventos.forEach(id -> jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.asignaciones_celula WHERE celula_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", id));
        grupos.forEach(id -> jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("A2: una Mentoria del Alquimista para todos la ve el aprendiz, la abre, y le genera su recordatorio")
    void laMentoriaLeLlegaAlAprendiz() {
        UUID grupo = grupo(usuarioMentor());
        UserId aprendiz = aprendizCon(grupo);
        EventoId mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.TODOS, null);

        assertThat(obtener.obtener(aprendiz, mentoria).evento().id()).isEqualTo(mentoria);
        assertThat(idsEnLaListaDe(aprendiz)).contains(mentoria.value());

        generar.generar(Instant.now());
        assertThat(recordatoriosDe(mentoria, aprendiz)).as("recordatorio de 10 min antes").isPositive();
    }

    @Test
    @DisplayName("E-363: el mentor del grupo y los integrantes adicionales abren el evento del grupo y reciben su aviso")
    void elMentorYLosAdicionalesAbrenElEventoDelGrupo() {
        UserId mentor = usuarioMentor();
        UUID grupo = grupo(mentor);
        UUID otroGrupoDelMentor = grupo(mentor);          // D-141: el mismo mentor lidera los dos
        UserId deAca = aprendizCon(grupo);
        UserId sumado = aprendizCon(otroGrupoDelMentor);  // D-139: su principal es el otro grupo...
        asignar(grupo, sumado, "APRENDIZ");               // ...y tambien esta en este
        UserId ajeno = aprendizCon(grupo(usuarioMentor()));
        EventoId delGrupo = evento(TipoEvento.ESPONTANEO, TipoAudiencia.CELULA, grupo);
        EventoId delOtroGrupo = evento(TipoEvento.ESPONTANEO, TipoAudiencia.CELULA, otroGrupoDelMentor);

        assertThat(obtener.obtener(mentor, delGrupo).evento().id()).isEqualTo(delGrupo);
        assertThat(obtener.obtener(mentor, delOtroGrupo).evento().id()).isEqualTo(delOtroGrupo);
        assertThat(idsEnLaListaDe(mentor)).contains(delGrupo.value(), delOtroGrupo.value());
        assertThat(obtener.obtener(sumado, delGrupo).evento().id()).isEqualTo(delGrupo);
        assertThat(obtener.obtener(deAca, delGrupo).evento().id()).isEqualTo(delGrupo);
        assertThatThrownBy(() -> obtener.obtener(ajeno, delGrupo)).isInstanceOf(NotAuthorizedException.class);
        assertThat(idsEnLaListaDe(ajeno)).doesNotContain(delGrupo.value());

        generar.generar(Instant.now());
        assertThat(recordatoriosDe(delGrupo, sumado)).as("el integrante adicional recibe el aviso").isPositive();
        assertThat(recordatoriosDe(delGrupo, deAca)).isPositive();
        assertThat(recordatoriosDe(delGrupo, mentor)).isPositive();
        assertThat(recordatoriosDe(delGrupo, ajeno)).isZero();
    }

    @Test
    @DisplayName("SEG-13: crear un evento con un link javascript: se rechaza (400) y no se guarda nada")
    void unLinkJavascriptSeRechaza() {
        int antes = jdbc.queryForObject("SELECT count(*) FROM renaser.eventos WHERE creado_por = ?", Integer.class,
                admin.value());

        assertThatThrownBy(() -> crear.crear(comando(TipoEvento.SESION_ESPECIAL, TipoAudiencia.TODOS, null,
                "javascript:alert(1)")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El link tiene que empezar con https:// o http://");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.eventos WHERE creado_por = ?", Integer.class,
                admin.value())).isEqualTo(antes);
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private EventoId evento(TipoEvento tipo, TipoAudiencia audiencia, UUID grupo) {
        EventoId id = crear.crear(comando(tipo, audiencia, grupo, "https://meet.google.com/abc-defg-hij")).evento().id();
        eventos.add(id.value());
        return id;
    }

    /** Mañana, a esta hora y minuto exactos: los avisos de "06:00" y de "10 min antes" quedan en el futuro. */
    private CrearEventoCommand comando(TipoEvento tipo, TipoAudiencia audiencia, UUID grupo, String link) {
        Instant inicio = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        return new CrearEventoCommand(admin, "Evento " + tipo.name().substring(0, 8), null, inicio, 60, LIMA,
                TipoUbicacion.MEET, link, audiencia, null, null, grupo, tipo, false, false, false, null, Set.of(),
                List.of());
    }

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))",
                id, "eventos-" + id + "@renaser.test", "Persona " + rol, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId usuarioMentor() {
        UserId mentor = usuario("MENTOR");
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mentor.value());
        return mentor;
    }

    /** El grupo con su mentor en {@code celulas.mentor_id} y en una asignacion vigente, como lo deja community. */
    private UUID grupo(UserId mentor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id) VALUES (?, ?, ?, ?)",
                id, "Grupo " + id.toString().substring(0, 6), mentor.value(), cohorte);
        grupos.add(id);
        asignar(id, mentor, "MENTOR");
        return id;
    }

    /** Aprendiz inscrito, con el puntero en su grupo principal y su asignacion vigente en el. */
    private UserId aprendizCon(UUID grupoPrincipal) {
        UserId aprendiz = usuario("APRENDIZ");
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id)
                VALUES (?, 10, 'America/Lima', ?)
                """, aprendiz.value(), grupoPrincipal);
        asignar(grupoPrincipal, aprendiz, "APRENDIZ");
        return aprendiz;
    }

    private void asignar(UUID grupo, UserId usuario, String funcion) {
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 day', 'ADMINISTRATIVO', ?)
                """, grupo, usuario.value(), funcion, "prueba-" + UUID.randomUUID());
    }

    private List<UUID> idsEnLaListaDe(UserId visor) {
        Instant ahora = Instant.now();
        return listar.listar(visor, ahora, ahora.plus(Duration.ofDays(3))).stream()
                .map(ocurrencia -> ocurrencia.evento().id().value())
                .toList();
    }

    private int recordatoriosDe(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ?",
                Integer.class, evento.value(), usuario.value());
    }
}
