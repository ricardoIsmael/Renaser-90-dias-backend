package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quién puede entrar a la sala (spec 040, FR-023, decisión 2 del dueño: abierta o con permiso, en cada evento) y el «Reintentar» a mano, sin
 * duplicar nada si Google ya había creado la sala (FR-007, SC-011).
 */
class AccesoYReintentoDeLasSalasTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    // spec 040 FR-023, SC-013
    @Test
    @DisplayName("SC-013: por defecto la sala queda ABIERTA (OPEN) y se lee de vuelta lo que Google confirmó")
    void porDefectoLaSalaQuedaAbierta() {
        EventoConMeetVista creado = crear(AccesoSalaMeet.porDefecto());

        SalaMeet sala = m.salas.todas().get(0);
        assertThat(AccesoSalaMeet.porDefecto()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(sala.accesoElegido()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(sala.accesoConfirmado()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(m.google.accesoPorEnlace).containsEntry(sala.enlace(), AccesoSalaMeet.ABIERTA);
        assertThat(creado.meet().accesoAplicado()).isTrue();
        assertThat(creado.meet().acceso()).isEqualTo(AccesoSalaMeet.ABIERTA);
        // El acceso se pide SIEMPRE, también el de por defecto: no se depende de lo que Google ponga solo.
        assertThat(m.google.llamadasDe("acceso:")).isEqualTo(1);
    }

    // spec 040 FR-023, SC-013
    @Test
    @DisplayName("SC-013: elegir «con permiso» deja la sala TRUSTED, y la pantalla de Administración lo dice")
    void conPermisoQuedaConPermiso() {
        EventoConMeetVista creado = crear(AccesoSalaMeet.CON_PERMISO);

        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.accesoConfirmado()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
        assertThat(m.google.llamadasDe("acceso:")).isEqualTo(1);
        assertThat(creado.meet().acceso()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
        assertThat(creado.meet().accesoAplicado()).isTrue();
    }

    // spec 040 FR-023, SC-013
    @Test
    @DisplayName("SC-013: si Google no deja cambiar el acceso, el evento y el link se conservan y Administración lee «La sala quedó con permiso; no pudimos dejarla abierta.»")
    void siGoogleNoAceptaElAccesoElEventoYElLinkSeConservan() {
        m.google.noDejaCambiarElAcceso = true;

        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);

        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(creado.evento().evento().valorUbicacion()).isEqualTo(sala.enlace()).startsWith("https://meet.google.com/");
        assertThat(sala.accesoElegido()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(sala.accesoConfirmado()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
        assertThat(creado.meet().accesoAplicado()).isFalse();
        assertThat(creado.meet().mensaje()).isEqualTo("La sala quedó con permiso; no pudimos dejarla abierta.");
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: «Reintentar» vuelve a dejar la sala como se eligió cuando Google ya lo permite")
    void reintentarCorrigeElAcceso() {
        m.google.noDejaCambiarElAcceso = true;
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        m.google.noDejaCambiarElAcceso = false;

        EventoConMeetVista reintentado = m.reintento.reintentar(ADMIN, creado.evento().evento().id());

        assertThat(reintentado.meet().accesoAplicado()).isTrue();
        assertThat(reintentado.meet().mensaje()).isNull();
        assertThat(m.salas.todas().get(0).accesoConfirmado()).isEqualTo(AccesoSalaMeet.ABIERTA);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: cambiar quién puede entrar en un evento ya creado actualiza las salas de las fechas que todavía no pasaron")
    void cambiarElAccesoDeUnEventoYaCreado() {
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        Evento e = creado.evento().evento();

        m.servicio.actualizar(new ActualizarEventoCommand(ADMIN, e.id(), e.titulo(), null, e.iniciaEn(), 90, LIMA, TipoUbicacion.MEET,
                e.valorUbicacion(), TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(), List.of(),
                AccesoSalaMeet.CON_PERMISO));

        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.accesoElegido()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
        assertThat(sala.accesoConfirmado()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
        assertThat(m.eventos.byId(e.id()).orElseThrow().accesoSalaMeet()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-007, SC-011
    @Test
    @DisplayName("SC-011: si Google creó la sala pero la respuesta se perdió, reintentar toma esa sala: un solo evento en Google y el mismo link")
    void respuestaPerdidaNoDuplicaLaSala() {
        m.google.perderLaRespuestaAlCrear = true;
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        assertThat(m.google.salasCreadas()).as("Google sí la había creado").isEqualTo(1);

        EventoConMeetVista reintentado = m.reintento.reintentar(ADMIN, creado.evento().evento().id());

        assertThat(m.google.salasCreadas()).isEqualTo(1);
        assertThat(reintentado.evento().evento().valorUbicacion()).isEqualTo(m.google.eventos.values().iterator().next());
        assertThat(reintentado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
    }

    // spec 040 FR-007, SC-011
    @Test
    @DisplayName("SC-011: un doble toque en «Reintentar» (o repetir la creación) deja un solo evento en Google y el mismo link")
    void dobleToqueNoDuplica() {
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        String enlace = creado.evento().evento().valorUbicacion();

        m.reintento.reintentar(ADMIN, creado.evento().evento().id());
        EventoConMeetVista otraVez = m.reintento.reintentar(ADMIN, creado.evento().evento().id());

        assertThat(m.google.salasCreadas()).isEqualTo(1);
        assertThat(otraVez.evento().evento().valorUbicacion()).isEqualTo(enlace);
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: «Reintentar» a mano no espera el turno del reintento automático, ni aunque ya se haya rendido")
    void reintentarAManoIgnoraLaEspera() {
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        for (int vez = 0; vez < 12; vez++) {
            m.reloj.avanzar(Duration.ofMinutes(30));
            m.trabajo.reintentarLoPendiente(m.reloj.now());
        }
        assertThat(m.salas.todas().get(0).proximoIntento()).isNull();
        m.google.fallaSiempre = null;

        EventoConMeetVista reintentado = m.reintento.reintentar(ADMIN, creado.evento().evento().id());

        assertThat(reintentado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(reintentado.evento().evento().valorUbicacion()).startsWith("https://meet.google.com/");
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: un evento que no pidió el enlace automático no se reintenta (409)")
    void reintentarUnEventoSinMeetEsUn409() {
        Evento sinMeet = Evento.crear(com.renaser.os.calendar.domain.model.evento.EventoId.of(m.ids.newId()), "A mano", null, HOY_19H_LIMA, 60,
                LIMA, TipoUbicacion.ZOOM, "https://zoom.us/j/1", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false,
                false, false, null, Set.of(), List.of(), ADMIN, m.reloj);
        m.eventos.guardar(sinMeet);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> m.reintento.reintentar(ADMIN, sinMeet.id()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Este evento no pide el enlace de Meet automático.");
    }

    // ─── Armado ─────────────────────────────────────────────────────────────────

    private EventoConMeetVista crear(AccesoSalaMeet acceso) {
        return m.servicio.crear(new CrearEventoCommand(ADMIN, "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, null,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null, acceso));
    }
}
