package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase;
import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase.Opciones;
import com.renaser.os.calendar.application.ports.in.audiencia.PersonaConvocable;
import com.renaser.os.calendar.application.ports.in.audiencia.ResumenDeAudiencia;
import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.in.meet.EventosConMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ReintentarSalaDeMeetUseCase;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El contrato HTTP de la spec 044 (contracts/api.md): la audiencia en crear y editar, las opciones, la vista previa y los errores con su código. */
@WebMvcTest({EventoController.class, AudienciaController.class})
@AutoConfigureMockMvc(addFilters = false)
class EventosPorAudienciaControllerTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-06T15:00:00Z"));
    private final UserId actor = UserId.of(UUID.randomUUID());

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
    @MockitoBean
    private ConvocatoriaUseCase convocatoria;

    private Evento evento() {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Rescate", null, Instant.parse("2026-11-10T01:00:00Z"), 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij", TipoAudiencia.SELECCION, null, null, null, TipoEvento.SESION_ESPECIAL,
                false, false, false, null, Set.of(), List.of(), actor, CLOCK, Formacion.ESCUELA, null);
    }

    private static final String CUERPO = """
            {"title":"Rescate","eventType":"SESION_ESPECIAL","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
             "locationValue":"https://meet.google.com/abc-defg-hij","audienceType":"SELECTION",
             "audience":{"parts":[{"kind":"TRAFFIC_LIGHT","color":"RED"},{"kind":"PERSON","id":"%s"}]}}
            """;

    // spec 044 FR-003, FR-032
    @Test
    @DisplayName("crear con audiencia: SELECTION y sus partes llegan al caso de uso traducidas")
    void crearConAudiencia() throws Exception {
        UUID persona = UUID.randomUUID();
        when(eventosUseCase.crear(any())).thenReturn(new EventoConMeetVista(new EventoVista(evento(), null), null));

        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(persona)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audienceType").value("SELECTION"));

        ArgumentCaptor<CrearEventoCommand> c = ArgumentCaptor.forClass(CrearEventoCommand.class);
        verify(eventosUseCase).crear(c.capture());
        assertThat(c.getValue().tipoAudiencia()).isEqualTo(TipoAudiencia.SELECCION);
        assertThat(c.getValue().audiencia().partes()).containsExactly(ParteDeAudiencia.color(ColorDeAudiencia.ROJO), ParteDeAudiencia.persona(persona));
    }

    // spec 044 FR-030
    @Test
    @DisplayName("FR-030: editar sin «audience» (una app anterior) no manda audiencia: el servicio conserva la guardada")
    void editarSinAudiencia() throws Exception {
        Evento e = evento();
        when(eventosUseCase.actualizar(any())).thenReturn(new EventoConMeetVista(new EventoVista(e, null), null));

        mockMvc.perform(put("/api/v1/calendar/events/" + e.id()).header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rescate","eventType":"SESION_ESPECIAL","startsAt":"2026-11-10T01:00:00Z","locationType":"MEET",
                                 "locationValue":"https://meet.google.com/abc-defg-hij","audienceType":"SELECTION"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ActualizarEventoCommand> c = ArgumentCaptor.forClass(ActualizarEventoCommand.class);
        verify(eventosUseCase).actualizar(c.capture());
        assertThat(c.getValue().audiencia()).isNull();
    }

    // spec 044 FR-004
    @Test
    @DisplayName("FR-004: fuera del alcance es 403 FUERA_DE_TU_ALCANCE con los ids rechazados")
    void fueraDeAlcance() throws Exception {
        UUID ajeno = UUID.randomUUID();
        when(eventosUseCase.crear(any())).thenThrow(AudienciaRechazadaException.fueraDeTuAlcance(List.of(ajeno)));

        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(ajeno)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_TU_ALCANCE"))
                .andExpect(jsonPath("$.ids[0]").value(ajeno.toString()));
    }

    // spec 044 FR-005
    @Test
    @DisplayName("FR-005: audiencia vacía es 400 AUDIENCIA_VACIA; una parte que no se entiende, 400 AUDIENCIA_INVALIDA")
    void vaciaEInvalida() throws Exception {
        when(eventosUseCase.crear(any())).thenThrow(AudienciaRechazadaException.vacia());
        mockMvc.perform(post("/api/v1/calendar/events").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("AUDIENCIA_VACIA"));

        mockMvc.perform(post("/api/v1/calendar/audience/preview").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"kind\":\"TRAFFIC_LIGHT\",\"color\":\"PURPLE\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("AUDIENCIA_INVALIDA"));
    }

    // spec 044 FR-016, FR-018, FR-019
    @Test
    @DisplayName("FR-016/018/019: el evento trae «audience» solo si el servicio la da; «organizer» siempre")
    void audienciaYOrganizador() throws Exception {
        Evento e = evento();
        when(eventosUseCase.obtener(any(), any())).thenReturn(new EventoConMeetVista(new EventoVista(e, null), null));
        when(convocatoria.organizador(any())).thenReturn(Optional.of(new PersonaConvocable(actor.value(), "Rocío Díaz", null,
                "Líder de mentores", null)));

        mockMvc.perform(get("/api/v1/calendar/events/" + e.id()).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").doesNotExist())
                .andExpect(jsonPath("$.organizer.name").value("Rocío Díaz"))
                .andExpect(jsonPath("$.organizer.role").value("Líder de mentores"));

        when(convocatoria.resumenPara(any(), any())).thenReturn(Optional.of(new ResumenDeAudiencia(List.of(
                new ResumenDeAudiencia.Parte(TipoDeParte.COLOR, null, null, ColorDeAudiencia.ROJO, 15, CLOCK.now(), List.of())), 15)));
        mockMvc.perform(get("/api/v1/calendar/events/" + e.id()).header("X-Actor-Id", actor.toString()))
                .andExpect(jsonPath("$.audience.total").value(15))
                .andExpect(jsonPath("$.audience.parts[0].kind").value("TRAFFIC_LIGHT"))
                .andExpect(jsonPath("$.audience.parts[0].color").value("RED"));
    }

    // spec 044 FR-029, FR-033
    @Test
    @DisplayName("FR-029: las opciones dicen si se puede crear y qué; FR-033: la vista previa dice a cuántos llega")
    void opcionesYVistaPrevia() throws Exception {
        when(convocatoria.opciones(eq(actor), any())).thenReturn(new Opciones(true, EnumSet.of(TipoDeParte.MIS_ALUMNOS, TipoDeParte.COLOR),
                Set.of(TipoEvento.SESION_ESPECIAL), TipoDeParte.MIS_ALUMNOS, false, Map.of("ROJO", 4), 6, List.of(), List.of()));
        when(convocatoria.vistaPrevia(eq(actor), any(), any())).thenReturn(new ResumenDeAudiencia(List.of(), 6));

        mockMvc.perform(get("/api/v1/calendar/audience/options").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCreate").value(true))
                .andExpect(jsonPath("$.defaultPart").value("MY_LEARNERS"))
                .andExpect(jsonPath("$.trafficLight.RED").value(4))
                .andExpect(jsonPath("$.canChooseAll").value(false));
        mockMvc.perform(post("/api/v1/calendar/audience/preview").header("X-Actor-Id", actor.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"kind\":\"MY_LEARNERS\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
    }
}
