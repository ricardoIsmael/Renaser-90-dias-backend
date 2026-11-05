package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.ALQUIMISTA;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión 6 del dueño (2026-11-05, spec 040): quien crea el evento va como invitado de la reunión, y la sala graba y transcribe sola. Si la cuenta
 * de Google conectada no tiene el plan, el evento y la sala se conservan y Administración ve qué hace falta. Con el Google simulado: sin red.
 */
class GrabacionYAnfitrionDeLaSalaTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    // ─── Quien crea el evento va como invitado (FR-035) ─────────────────────────

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: al crear la sala, el correo de quien crea el evento va como invitado del evento de Calendar")
    void quienCreaElEventoVaComoInvitado() {
        m.servicio.crear(comando(ADMIN));

        PedidoDeSala pedido = m.google.ultimoPedido.values().iterator().next();
        assertThat(pedido.invitados()).containsExactly("admin@renaser.pe");
    }

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: si edita otra persona (el Alquimista), quedan invitados quien lo creó y quien lo edita")
    void alEditarSeSumaQuienEdita() {
        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        m.servicio.actualizar(edicion(ALQUIMISTA, creado, "Clase nueva"));

        PedidoDeSala pedido = m.google.ultimoPedido.values().iterator().next();
        assertThat(pedido.titulo()).isEqualTo("Clase nueva");
        assertThat(pedido.invitados()).containsExactly("admin@renaser.pe", "alquimista@gmail.com");
    }

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: el proceso en segundo plano no sabe quién edita y no toca los invitados que Google ya tiene")
    void elProcesoDeFondoNoTocaLosInvitados() {
        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
        m.servicio.actualizar(edicion(ADMIN, creado, "Clase nueva"));

        m.google.fallaSiempre = null;
        m.reloj.avanzar(Duration.ofMinutes(3));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        PedidoDeSala pedido = m.google.ultimoPedido.values().iterator().next();
        assertThat(pedido.titulo()).isEqualTo("Clase nueva");
        assertThat(pedido.invitados()).isNull();
    }

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: con EVENTOS_MEET_INVITAR_AL_CREADOR apagado no se invita a nadie")
    void sinInvitarAlCreador() {
        m.invitarAlCreador = false;

        m.servicio.crear(comando(ADMIN));

        assertThat(m.google.ultimoPedido.values().iterator().next().invitados()).isEmpty();
    }

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: si no se puede leer el correo, la sala se crea igual, sin invitados")
    void sinCorreoLaSalaSeCreaIgual() {
        m.fallaAlBuscarCorreos = new IllegalStateException("users no responde");

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(m.google.ultimoPedido.values().iterator().next().invitados()).isEmpty();
    }

    // ─── Grabación y transcripción (FR-036, FR-037) ─────────────────────────────

    // spec 040 FR-036, SC-014
    @Test
    @DisplayName("FR-036: al crear la sala se pide que grabe, transcriba y tome notas sola, y queda anotado lo que Google confirmó")
    void laSalaGrabaYTranscribeSola() {
        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        assertThat(m.google.llamadasDe("grabacion:")).isEqualTo(1);
        assertThat(m.google.llamadas).as("primero se abre la sala, después se pide la grabación")
                .filteredOn(l -> l.startsWith("acceso:") || l.startsWith("grabacion:"))
                .extracting(l -> l.substring(0, l.indexOf(':'))).containsExactly("acceso", "grabacion");
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::artefactos)
                .isEqualTo(new ArtefactosDeLaSala(true, true, true));
        assertThat(creado.meet().avisoDelPlan()).isNull();
        assertThat(m.auditoriaDeGoogle.filas).anyMatch(f -> f.accion() == AccionDeAuditoriaGoogle.AJUSTAR_GRABACION && f.exito());
    }

    // spec 040 FR-037, SC-015
    @Test
    @DisplayName("FR-037: sin el plan, el evento y la sala se conservan, no se reintenta, y Administración ve qué hace falta")
    void sinElPlanSeConservaTodoYSeAvisa() {
        m.google.sinPlanParaGrabar = true;
        m.google.sinPlanParaTranscribir = true;

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        Evento evento = creado.evento().evento();
        assertThat(evento.valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(creado.meet().mensaje()).isNull();
        assertThat(creado.meet().avisoDelPlan()).isEqualTo("Para grabar hace falta Google One de 2 TB o más en la cuenta conectada; "
                + "para transcribir, Google Workspace; para las notas automáticas, Gemini en Google Workspace.");
        assertThat(m.salas.todas()).singleElement().satisfies(s -> {
            assertThat(s.proximoIntento()).as("no deja trabajo pendiente").isNull();
            assertThat(s.artefactos()).isEqualTo(new ArtefactosDeLaSala(false, false, false));
        });

        m.reloj.avanzar(Duration.ofHours(1));
        m.trabajo.reintentarLoPendiente(m.reloj.now());
        assertThat(m.google.llamadasDe("grabacion:")).as("reintentar no cambia el plan de la cuenta").isEqualTo(1);
    }

    // spec 040 FR-037
    @Test
    @DisplayName("FR-037: con Google One de 2 TB graba pero no transcribe: el aviso solo nombra lo que falta")
    void avisoSoloDeLoQueFalta() {
        m.google.sinPlanParaTranscribir = true;

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        assertThat(creado.meet().avisoDelPlan())
                .isEqualTo("Para transcribir hace falta Google Workspace; para las notas automáticas, Gemini en Google Workspace.");
    }

    // spec 040 FR-036
    @Test
    @DisplayName("FR-036: con EVENTOS_MEET_GRABAR, EVENTOS_MEET_TRANSCRIBIR y EVENTOS_MEET_NOTAS apagados no se le pide nada a Google")
    void apagadoNoSePideNada() {
        m.grabar = false;
        m.transcribir = false;
        m.tomarNotas = false;

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        assertThat(m.google.llamadasDe("grabacion:")).isZero();
        assertThat(creado.meet().avisoDelPlan()).isNull();
    }

    // spec 040 FR-037, SC-003
    @Test
    @DisplayName("FR-037: si Google falla al pedir la grabación, el evento y su enlace quedan bien y sin error para la persona")
    void unFalloAlPedirLaGrabacionNoRompeNada() {
        m.google.alLlamar = () -> {
            if (m.google.llamadas.get(m.google.llamadas.size() - 1).startsWith("grabacion:")) {
                m.google.fallaProxima = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
            }
        };

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN));

        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(creado.meet().mensaje()).isNull();
        assertThat(creado.meet().avisoDelPlan()).isNull();
        assertThat(creado.evento().evento().valorUbicacion()).startsWith("https://meet.google.com/");
    }

    private static CrearEventoCommand comando(UserId actor) {
        return new CrearEventoCommand(actor, "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, null,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null,
                AccesoSalaMeet.ABIERTA);
    }

    private static ActualizarEventoCommand edicion(UserId actor, EventoConMeetVista previo, String titulo) {
        Evento e = previo.evento().evento();
        return new ActualizarEventoCommand(actor, e.id(), titulo, null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, e.valorUbicacion(),
                TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(), List.of(), AccesoSalaMeet.ABIERTA);
    }
}
