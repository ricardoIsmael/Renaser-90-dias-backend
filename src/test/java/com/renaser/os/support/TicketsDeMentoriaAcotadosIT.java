package com.renaser.os.support;

import com.renaser.os.EventosPendientes;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase.TicketsMentorPage;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase.ResponderTicketMentorCommand;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los tickets de mentoría acotados a quien acompaña (spec 050, D19), contra Postgres de verdad: lo que un doble no puede probar es que
 * «acompaña hoy» (asignaciones vigentes, grupo en curso) y la consulta de los tickets de esos aprendices se encuentran bien, con el
 * cursor de la página incluido.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TicketsDeMentoriaAcotadosIT {

    private static final Instant AHORA = Instant.parse("2027-03-10T15:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;
    @Autowired private ListarTicketsMentorUseCase listar;
    @Autowired private ResponderTicketMentorUseCase responder;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> celulas = new ArrayList<>();
    private UUID cohorte;

    private UserId mentor1;
    private UserId mentor2;
    private UserId guia;
    private UserId exMentor;
    private UserId mentorDelGrupoCerrado;
    private UserId admin;
    private UserId alquimista;
    private UserId ana;
    private UserId luis;
    private UserId eva;
    private UserId nuevo;
    private UserId vieja;
    private TicketMentorId ticketDeAna;
    private TicketMentorId ticketDeLuis;
    private TicketMentorId ticketDeEva;
    private TicketMentorId ticketDeNuevo;
    private TicketMentorId ticketDeVieja;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        usuarios.clear();
        celulas.clear();
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte de tickets', DATE '2027-03-01')", cohorte);
        UUID grupo1 = grupo("REGULAR", "2027-03-01", "2027-04-30");
        UUID grupo2 = grupo("REGULAR", "2027-03-01", "2027-04-30");
        UUID recepcion = grupo("RECEPCION", "2027-03-01", "2027-04-30");
        UUID cerrado = grupo("REGULAR", "2027-01-01", "2027-02-01");

        mentor1 = mentor("MENTOR");
        mentor2 = mentor("MENTOR");
        guia = mentor("GUIA_APRENDIZ");
        exMentor = mentor("MENTOR");
        mentorDelGrupoCerrado = mentor("MENTOR");
        admin = usuario("ADMIN");
        alquimista = usuario("ALQUIMISTA");
        asignar(grupo1, exMentor, "MENTOR", "2027-02-01", "2027-03-05");
        asignar(grupo1, mentor1, "MENTOR", "2027-03-05", null);
        asignar(grupo2, mentor2, "MENTOR", "2027-03-01", null);
        asignar(recepcion, guia, "GUIA", "2027-03-01", null);
        asignar(cerrado, mentorDelGrupoCerrado, "MENTOR", "2027-01-01", null);

        ana = aprendiz(grupo1, mentor1);
        luis = aprendiz(grupo1, mentor1);
        eva = aprendiz(grupo2, mentor2);
        nuevo = aprendiz(recepcion, null);
        vieja = aprendiz(cerrado, mentorDelGrupoCerrado);

        ticketDeAna = ticket(ana, "2027-03-09T10:00:00Z");
        ticketDeLuis = ticket(luis, "2027-03-09T11:00:00Z");
        ticketDeEva = ticket(eva, "2027-03-09T12:00:00Z");
        ticketDeNuevo = ticket(nuevo, "2027-03-09T13:00:00Z");
        ticketDeVieja = ticket(vieja, "2027-03-09T14:00:00Z");
    }

    @AfterEach
    void limpiar() {
        EventosPendientes.esperarQueTerminen(jdbc);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        celulas.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("D19: un mentor y una guía ven solo los tickets de los aprendices que acompañan hoy; el exmentor, el del grupo cerrado y quien no acompaña, ninguno")
    void mentorYGuiaVenSoloLosDeSusAprendices() {
        assertThat(ids(listar.propios(mentor1, null))).containsExactlyInAnyOrder(ticketDeAna, ticketDeLuis);
        assertThat(ids(listar.propios(mentor2, null))).containsExactly(ticketDeEva);
        assertThat(ids(listar.propios(guia, null))).containsExactly(ticketDeNuevo);
        assertThat(listar.propios(exMentor, null).tickets()).as("dejó de acompañar el 5/3").isEmpty();
        assertThat(listar.propios(mentorDelGrupoCerrado, null).tickets()).as("su grupo ya cerró").isEmpty();
    }

    @Test
    @DisplayName("el aprendiz sigue viendo solo los suyos; ADMIN y ALCHEMIST ven todos por la vista de plataforma; un mentor y una guía no la usan")
    void aprendizYPlataforma() {
        assertThat(ids(listar.propios(ana, null))).containsExactly(ticketDeAna);
        assertThat(ids(listar.todos(admin, null))).contains(ticketDeAna, ticketDeLuis, ticketDeEva, ticketDeNuevo, ticketDeVieja);
        assertThat(ids(listar.todos(alquimista, null))).contains(ticketDeAna, ticketDeLuis, ticketDeEva, ticketDeNuevo, ticketDeVieja);
        assertThatThrownBy(() -> listar.todos(mentor1, null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> listar.todos(guia, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("D19 y E-38: el ticket de Ana lo responde su mentor asignado y no el mentor de otro grupo ni la guía")
    void soloSuMentorResponde() {
        assertThatThrownBy(() -> responder.responder(new ResponderTicketMentorCommand(ticketDeAna, mentor2, "No es mi aprendiz")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> responder.responder(new ResponderTicketMentorCommand(ticketDeAna, guia, "Tampoco la mía")))
                .isInstanceOf(NotAuthorizedException.class);

        TicketMentor respondido = responder.responder(new ResponderTicketMentorCommand(ticketDeAna, mentor1, "Prueba con una sola cosa"));

        assertThat(respondido.estado().estaRespondido()).isTrue();
    }

    @Test
    @DisplayName("la lista del mentor pagina bien: 30 por página, sin repetir ni perder ninguno, y nunca se cuela un ticket ajeno")
    void paginaDelMentor() {
        for (int i = 0; i < 33; i++) {
            ticket(ana, Instant.parse("2027-03-08T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        TicketsMentorPage primera = listar.propios(mentor1, null);
        assertThat(primera.tickets()).hasSize(30);
        assertThat(primera.nextCursor()).isNotNull();
        TicketsMentorPage segunda = listar.propios(mentor1, primera.nextCursor());

        Set<TicketMentorId> todos = new HashSet<>(ids(primera));
        todos.addAll(ids(segunda));
        // 33 + el de Ana + el de Luis = 35, y ninguno de Eva, de la recepción ni del grupo cerrado
        assertThat(primera.tickets().size() + segunda.tickets().size()).isEqualTo(35);
        assertThat(todos).hasSize(35).doesNotContain(ticketDeEva, ticketDeNuevo, ticketDeVieja);
        assertThat(segunda.nextCursor()).isNull();
    }

    private static List<TicketMentorId> ids(TicketsMentorPage pagina) {
        return pagina.tickets().stream().map(TicketMentor::id).toList();
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID grupo(String tipo, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin) "
                + "VALUES (?, 'Grupo de tickets', ?, CAST(? AS renaser.tipo_celula), DATE '" + inicio + "', DATE '" + fin + "')",
                id, cohorte, tipo);
        celulas.add(id);
        return id;
    }

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')", id, id + "@tickets-d19.test", rol + " de prueba", rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId mentor(String rol) {
        UserId id = usuario(rol);
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id.value());
        // Lo que hace la aplicación al dar de alta al personal: sin su formación activa no acompaña ningún grupo (spec 022).
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id.value(), rol);
        return id;
    }

    /** Un aprendiz de ese grupo, con su fila del programa (y su mentor asignado, si lo tiene). */
    private UserId aprendiz(UUID grupo, UserId mentorAsignado) {
        UserId id = usuario("APRENDIZ");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, timezone, celula_id, mentor_id) "
                + "VALUES (?, 10, DATE '2027-03-01', 'America/Lima', ?, CAST(? AS uuid))", id.value(), grupo,
                mentorAsignado == null ? null : mentorAsignado.value().toString());
        asignar(grupo, id, "APRENDIZ", "2027-03-01", null);
        return id;
    }

    private void asignar(UUID celula, UserId usuario, String funcion, String desde, String hasta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), CAST(? AS timestamptz), CAST(? AS timestamptz), "
                + "'ADMINISTRATIVO', ?)", id, celula, usuario.value(), funcion, desde + "T00:00:00Z",
                hasta == null ? null : hasta + "T00:00:00Z", "ticket-d19|" + id);
    }

    private TicketMentorId ticket(UserId participante, String creadoEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.tickets_mentor (id, participante_id, descripcion_bloqueo, soluciones_intentadas, impacto_meta_smart, creado_en) "
                + "VALUES (?, ?, 'Bloqueo', 'Intento', 'Impacto', CAST(? AS timestamptz))", id, participante.value(), creadoEn);
        return TicketMentorId.of(id);
    }
}
