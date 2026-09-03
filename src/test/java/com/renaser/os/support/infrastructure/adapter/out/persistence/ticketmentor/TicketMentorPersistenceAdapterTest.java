package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketMentorPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private TicketMentorPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    /** Un id nuevo por ticket: la identidad ya no la sortea la factoria, entra por el puerto IdGenerator. */
    private static TicketMentorId nuevoId() {
        return TicketMentorId.of(UUID.randomUUID());
    }

    private UserId sembrarParticipante() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into renaser.usuarios (id, email, nombre_completo, rol) values (?, ?, ?, 'APRENDIZ')",
                id, id + "@renaser.test", "Aspirante de Prueba");
        jdbc.update("insert into renaser.participantes_programa (usuario_id) values (?)", id);
        return UserId.of(id);
    }

    @Test
    void guardaYRecuperaUnTicketAbierto() {
        UserId participante = sembrarParticipante();
        TicketMentor ticket = TicketMentor.abrir(nuevoId(), participante, "No mantengo la racha de Santuario",
                "Probe apagar notificaciones", "Atrasa mi meta de 90 dias sin celular", CLOCK);

        var guardado = adapter.save(ticket);

        TicketMentor cargado = adapter.byId(guardado.id()).orElseThrow();
        assertThat(cargado.participanteId()).isEqualTo(participante);
        assertThat(cargado.descripcionBloqueo()).isEqualTo("No mantengo la racha de Santuario");
        assertThat(cargado.estado().estaAbierto()).isTrue();
    }

    @Test
    void traduceAmbosEstadosEnLasDosDirecciones() {
        UserId participante = sembrarParticipante();
        TicketMentor abierto = TicketMentor.abrir(nuevoId(), participante, "bloqueo", "solucion", "impacto", CLOCK);
        adapter.save(abierto);
        assertThat(adapter.byId(abierto.id()).orElseThrow().estado().estaAbierto()).isTrue();

        abierto.responder("Respuesta del mentor", CLOCK);
        adapter.save(abierto);
        TicketMentor respondido = adapter.byId(abierto.id()).orElseThrow();
        assertThat(respondido.estado().estaRespondido()).isTrue();
        assertThat(respondido.respuestaMentor()).isEqualTo("Respuesta del mentor");
        assertThat(respondido.respondidoEn()).isNotNull();
    }

    @Test
    void porParticipanteSoloDevuelveLosDeEseParticipante() {
        UserId participanteA = sembrarParticipante();
        UserId participanteB = sembrarParticipante();
        adapter.save(TicketMentor.abrir(nuevoId(), participanteA, "a1", "a1", "a1", CLOCK));
        adapter.save(TicketMentor.abrir(nuevoId(), participanteB, "b1", "b1", "b1", CLOCK));

        var tickets = adapter.porParticipante(participanteA, null, 10);

        assertThat(tickets).hasSize(1);
        assertThat(tickets.getFirst().participanteId()).isEqualTo(participanteA);
    }

    @Test
    void todosDevuelveLosDeCualquierParticipante() {
        UserId participanteA = sembrarParticipante();
        UserId participanteB = sembrarParticipante();
        adapter.save(TicketMentor.abrir(nuevoId(), participanteA, "a1", "a1", "a1", CLOCK));
        adapter.save(TicketMentor.abrir(nuevoId(), participanteB, "b1", "b1", "b1", CLOCK));

        assertThat(adapter.todos(null, 10)).hasSize(2);
    }

    @Test
    void guardarConParticipanteSinInscripcionFallaConMensajeClaro() {
        TicketMentor ticket = TicketMentor.abrir(nuevoId(), UserId.of(UUID.randomUUID()), "bloqueo", "solucion",
                "impacto", CLOCK);

        assertThatThrownBy(() -> adapter.save(ticket)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("participantes_programa");
    }

    @Test
    void buscarEnBibliotecaUsaElIndiceFullTextEnEspanolYSoloTraeGuardados() {
        UserId participante = sembrarParticipante();

        TicketMentor guardado = TicketMentor.abrir(nuevoId(), participante, "No logro mantener la racha de Santuario",
                "Probe apagar notificaciones toda la noche", "impacto", CLOCK);
        guardado.responder("Desactiva las notificaciones push y activa el modo avion antes de dormir", CLOCK);
        guardado.guardarEnBiblioteca();
        adapter.save(guardado);

        TicketMentor noGuardado = TicketMentor.abrir(nuevoId(), participante, "Tambien tengo problemas con Santuario",
                "No probe nada todavia", "impacto", CLOCK);
        noGuardado.responder("Otra respuesta sobre Santuario que menciona notificaciones", CLOCK);
        adapter.save(noGuardado); // respondido pero NUNCA guardado en biblioteca

        var resultados = adapter.buscar("notificaciones Santuario", 5);

        assertThat(resultados).hasSize(1);
        assertThat(resultados.getFirst().descripcionBloqueo()).contains("Santuario");
        assertThat(resultados.getFirst().respuestaMentor()).contains("modo avion");
    }

    @Test
    void buscarEnBibliotecaSinCoincidenciasDevuelveVacio() {
        UserId participante = sembrarParticipante();
        TicketMentor guardado = TicketMentor.abrir(nuevoId(), participante, "bloqueo sobre habitos matutinos",
                "solucion", "impacto", CLOCK);
        guardado.responder("respuesta sobre rutina de manana", CLOCK);
        guardado.guardarEnBiblioteca();
        adapter.save(guardado);

        assertThat(adapter.buscar("facturacion criptomonedas inexistente", 5)).isEmpty();
    }
}
