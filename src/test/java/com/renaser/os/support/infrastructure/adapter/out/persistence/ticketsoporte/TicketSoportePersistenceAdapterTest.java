package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketsoporte;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.AdjuntoSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT contra Postgres real (Testcontainers) — mismo patron que
 * TicketMentorPersistenceAdapterTest / AccountRequestPersistenceAdapterTest de `users`.
 * `tickets_soporte.usuario_id` referencia `usuarios(id)` directo (sin
 * participantes_programa de por medio, a diferencia de tickets_mentor), asi que solo
 * hace falta sembrar la fila de `usuarios`.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketSoportePersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private TicketSoportePersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private UserId sembrarUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into renaser.usuarios (id, email, nombre_completo, rol) values (?, ?, ?, 'APRENDIZ')",
                id, id + "@renaser.test", "Usuario de Prueba");
        return UserId.of(id);
    }

    @Test
    void guardaYRecuperaUnTicketSinAdjunto() {
        UserId usuario = sembrarUsuario();
        TicketSoporte ticket = TicketSoporte.abrir(usuario, CategoriaSoporte.TECNICO, "La app se cierra",
                "Se cierra sola al abrir el modulo de habitos", "GET /habits -> 500", null, CLOCK);

        var guardado = adapter.save(ticket);

        TicketSoporte cargado = adapter.byId(guardado.id()).orElseThrow();
        assertThat(cargado.usuarioId()).isEqualTo(usuario);
        assertThat(cargado.categoria()).isEqualTo(CategoriaSoporte.TECNICO);
        assertThat(cargado.adjunto()).isNull();
        assertThat(cargado.estado()).isEqualTo(EstadoTicketSoporte.ABIERTO);
    }

    @Test
    void guardaYRecuperaUnTicketConAdjunto() {
        UserId usuario = sembrarUsuario();
        AdjuntoSoporte adjunto = new AdjuntoSoporte("renaser-files", "soporte/" + usuario.value() + "/1.png");
        TicketSoporte ticket = TicketSoporte.abrir(usuario, CategoriaSoporte.CUENTA, "No puedo entrar",
                "Me sale credenciales invalidas aunque la contrasena es correcta", null, adjunto, CLOCK);

        adapter.save(ticket);

        TicketSoporte cargado = adapter.byId(ticket.id()).orElseThrow();
        assertThat(cargado.adjunto()).isEqualTo(adjunto);
    }

    @Test
    void traduceAmbosEstadosEnLasDosDirecciones() {
        UserId usuario = sembrarUsuario();
        TicketSoporte ticket = TicketSoporte.abrir(usuario, CategoriaSoporte.OTRO, "Consulta", "Duda general sobre el programa de 90 dias",
                null, null, CLOCK);
        adapter.save(ticket);
        assertThat(adapter.byId(ticket.id()).orElseThrow().estado()).isEqualTo(EstadoTicketSoporte.ABIERTO);

        ticket.resolver("Ya te lo explicamos por chat", CLOCK);
        adapter.save(ticket);
        TicketSoporte resuelto = adapter.byId(ticket.id()).orElseThrow();
        assertThat(resuelto.estado()).isEqualTo(EstadoTicketSoporte.RESUELTO);
        assertThat(resuelto.notasAdmin()).isEqualTo("Ya te lo explicamos por chat");
    }

    @Test
    void porUsuarioSoloDevuelveLosDeEseUsuario() {
        UserId usuarioA = sembrarUsuario();
        UserId usuarioB = sembrarUsuario();
        adapter.save(TicketSoporte.abrir(usuarioA, CategoriaSoporte.OTRO, "a", "un mensaje de diez caracteres",
                null, null, CLOCK));
        adapter.save(TicketSoporte.abrir(usuarioB, CategoriaSoporte.OTRO, "b", "un mensaje de diez caracteres",
                null, null, CLOCK));

        var propios = adapter.porUsuario(usuarioA);

        assertThat(propios).hasSize(1);
        assertThat(propios.getFirst().usuarioId()).isEqualTo(usuarioA);
    }

    @Test
    void todosFiltraPorEstadoCuandoSePide() {
        UserId usuario = sembrarUsuario();
        TicketSoporte abierto = TicketSoporte.abrir(usuario, CategoriaSoporte.OTRO, "abierto",
                "un mensaje de diez caracteres", null, null, CLOCK);
        TicketSoporte resuelto = TicketSoporte.abrir(usuario, CategoriaSoporte.OTRO, "resuelto",
                "un mensaje de diez caracteres", null, null, CLOCK);
        resuelto.resolver(null, CLOCK);
        adapter.save(abierto);
        adapter.save(resuelto);

        assertThat(adapter.todos(EstadoTicketSoporte.ABIERTO)).hasSize(1);
        assertThat(adapter.todos(EstadoTicketSoporte.RESUELTO)).hasSize(1);
        assertThat(adapter.todos(null)).hasSize(2);
    }

    @Test
    void categoriaFacturacionYCuentaTraducenBienElEnumEnEspanol() {
        UserId usuario = sembrarUsuario();
        adapter.save(TicketSoporte.abrir(usuario, CategoriaSoporte.FACTURACION, "cobro duplicado",
                "me cobraron dos veces la suscripcion", null, null, CLOCK));
        adapter.save(TicketSoporte.abrir(usuario, CategoriaSoporte.CUENTA, "no entro", "credenciales invalidas todo el tiempo",
                null, null, CLOCK));

        var todos = adapter.porUsuario(usuario);

        assertThat(todos).extracting(TicketSoporte::categoria)
                .containsExactlyInAnyOrder(CategoriaSoporte.FACTURACION, CategoriaSoporte.CUENTA);
    }
}
