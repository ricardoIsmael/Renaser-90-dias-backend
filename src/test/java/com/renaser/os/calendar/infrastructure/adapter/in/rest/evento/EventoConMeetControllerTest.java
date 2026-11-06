package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeet;
import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.in.meet.EventosConMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ReintentarSalaDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lo que el contrato HTTP de eventos dice del enlace de Meet automático (spec 040): lo que se pide al crear y lo que se lee de vuelta. */
@WebMvcTest(EventoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventoConMeetControllerTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-05T15:00:00Z"));

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EventosConMeetUseCase eventosUseCase;
    @MockitoBean
    private DisponibilidadDeMeetUseCase disponibilidadDeMeetUseCase;
    @MockitoBean
    private ReintentarSalaDeMeetUseCase reintentarSalaUseCase;
    @MockitoBean
    private SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase;
    @MockitoBean
    private ConfirmarPortadaUseCase confirmarPortadaUseCase;
    @MockitoBean
    private ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase;
    /** Spec 044: «Para quién es» y el organizador; sin stubs devuelve vacío. */
    @MockitoBean
    private com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase convocatoriaUseCase;

    private static Evento evento(UserId creador, String enlace, AccesoSalaMeet acceso) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Clase en vivo", null, Instant.parse("2026-11-10T01:00:00Z"), 90,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, enlace, TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false, false,
                false, null, Set.of(), List.of(), creador, CLOCK, Formacion.ESCUELA, acceso);
    }

    // spec 040 FR-001, FR-023
    @Test
    @DisplayName("FR-001, FR-023 (corregido 2026-11-05): con crearEnlaceMeet y sin elegir nada la sala es ABIERTA; un accesoSalaMeet que mande un cliente se ignora")
    void pedirElEnlaceAutomatico() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        when(eventosUseCase.crear(any())).thenReturn(new EventoConMeetVista(new EventoVista(evento(actor, null, AccesoSalaMeet.ABIERTA), null), null));

        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Clase en vivo","eventType":"ESPONTANEO","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
                                 "crearEnlaceMeet":true}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Clase en vivo","eventType":"ESPONTANEO","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
                                 "crearEnlaceMeet":true,"accesoSalaMeet":"CON_PERMISO"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearEventoCommand> captor = ArgumentCaptor.forClass(CrearEventoCommand.class);
        verify(eventosUseCase, org.mockito.Mockito.times(2)).crear(captor.capture());
        assertThat(captor.getAllValues()).extracting(CrearEventoCommand::accesoSalaMeet)
                .containsExactly(AccesoSalaMeet.ABIERTA, AccesoSalaMeet.ABIERTA);
    }

    // spec 040 FR-020
    @Test
    @DisplayName("FR-020: un cliente anterior, que no manda esos campos, pega el link a mano y no pide sala automática")
    void sinPedirNadaTodoEsComoSiempre() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        when(eventosUseCase.crear(any())).thenReturn(
                new EventoConMeetVista(new EventoVista(evento(actor, "https://meet.google.com/abc-defg-hij", null), null), null));

        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Clase en vivo","eventType":"ESPONTANEO","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
                                 "locationValue":"https://meet.google.com/abc-defg-hij"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationValue").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.meet").doesNotExist());

        ArgumentCaptor<CrearEventoCommand> captor = ArgumentCaptor.forClass(CrearEventoCommand.class);
        verify(eventosUseCase).crear(captor.capture());
        assertThat(captor.getValue().accesoSalaMeet()).isNull();
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023 (corregido 2026-11-05): un accesoSalaMeet cualquiera (de una app de la versión anterior) no rompe: se ignora y la sala es ABIERTA")
    void unAccesoQueYaNoExisteSeIgnora() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        when(eventosUseCase.crear(any())).thenReturn(new EventoConMeetVista(new EventoVista(evento(actor, null, AccesoSalaMeet.ABIERTA), null), null));

        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Clase en vivo","eventType":"ESPONTANEO","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
                                 "crearEnlaceMeet":true,"accesoSalaMeet":"PUBLICA"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearEventoCommand> captor = ArgumentCaptor.forClass(CrearEventoCommand.class);
        verify(eventosUseCase).crear(captor.capture());
        assertThat(captor.getValue().accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);
    }

    // spec 040 FR-004, FR-008
    @Test
    @DisplayName("FR-008: la respuesta del evento trae cómo va la sala (estado, acceso, mensaje corto y cuántas fechas tienen problema)")
    void laRespuestaTraeComoVaLaSala() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        Evento e = evento(actor, null, AccesoSalaMeet.ABIERTA);
        ResumenDeMeet resumen = new ResumenDeMeet(EstadoSalaMeet.FALLIDO, AccesoSalaMeet.ABIERTA, false, "No pudimos crear el enlace de Meet.", 0, 1);
        when(eventosUseCase.obtener(any(), any())).thenReturn(new EventoConMeetVista(new EventoVista(e, null), resumen));

        mockMvc.perform(get("/api/v1/calendar/events/{id}", e.id().value()).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meet.estado").value("FALLIDO"))
                .andExpect(jsonPath("$.meet.acceso").value("ABIERTA"))
                .andExpect(jsonPath("$.meet.mensaje").value("No pudimos crear el enlace de Meet."))
                .andExpect(jsonPath("$.meet.fechasConProblema").value(1));
    }

    // spec 040 FR-014
    @Test
    @DisplayName("FR-014: el formulario pregunta si puede ofrecer «Crear enlace de Meet» y recibe el motivo si no")
    void disponibilidad() throws Exception {
        when(disponibilidadDeMeetUseCase.consultar(any())).thenReturn(DisponibilidadDeMeet.no("SIN_CUENTA"));

        mockMvc.perform(get("/api/v1/calendar/events/meet/status").header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.motivo").value("SIN_CUENTA"));
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: «Reintentar» llama al caso de uso con el evento y devuelve cómo quedó")
    void reintentar() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        Evento e = evento(actor, "https://meet.google.com/abc-defg-hij", AccesoSalaMeet.ABIERTA);
        ResumenDeMeet resumen = new ResumenDeMeet(EstadoSalaMeet.CREADO, AccesoSalaMeet.ABIERTA, true, null, 1, 0);
        when(reintentarSalaUseCase.reintentar(any(), any())).thenReturn(new EventoConMeetVista(new EventoVista(e, null), resumen));

        mockMvc.perform(post("/api/v1/calendar/events/{id}/meet/retry", e.id().value()).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meet.estado").value("CREADO"))
                .andExpect(jsonPath("$.locationValue").value("https://meet.google.com/abc-defg-hij"));
        verify(reintentarSalaUseCase).reintentar(any(), any());
    }
}
