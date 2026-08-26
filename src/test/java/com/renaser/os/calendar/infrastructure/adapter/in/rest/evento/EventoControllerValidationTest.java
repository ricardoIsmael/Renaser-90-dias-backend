package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CancelarOcurrenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EliminarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de capa web para {@link EventoController} — descubierto sondeando la app real
 * contra Postgres (no algo que un test con mocks del servicio detecte): sin {@code @Valid}
 * en el {@code @RequestBody}, un {@code rsvp}/{@code cancel-occurrence} con
 * {@code occurrenceStart} ausente llegaba hasta {@code Instant.parse(null)} y reventaba con
 * 500 en vez de 400. Cubre tambien que {@code notifyOnCreate}/{@code remindByEmail} son
 * opcionales (Defecto 2 de la revision): el cliente movil los omite y antes eso mismo
 * rompia la deserializacion con {@code Cannot map `null` into type `boolean`}.
 */
@WebMvcTest(EventoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventoControllerValidationTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarEventosParaVisorUseCase listarUseCase;
    @MockitoBean
    private ObtenerEventoUseCase obtenerUseCase;
    @MockitoBean
    private CrearEventoUseCase crearUseCase;
    @MockitoBean
    private ActualizarEventoUseCase actualizarUseCase;
    @MockitoBean
    private EliminarEventoUseCase eliminarUseCase;
    @MockitoBean
    private CancelarOcurrenciaUseCase cancelarOcurrenciaUseCase;
    @MockitoBean
    private SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase;
    @MockitoBean
    private ConfirmarPortadaUseCase confirmarPortadaUseCase;
    @MockitoBean
    private ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase;

    @Test
    void rsvpConCuerpoSinCamposObligatoriosDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(put("/api/v1/calendar/events/{id}/rsvp", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(confirmarAsistenciaUseCase);
    }

    @Test
    void cancelarOcurrenciaConCuerpoSinOccurrenceStartDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/calendar/events/{id}/cancel-occurrence", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cancelarOcurrenciaUseCase);
    }

    @Test
    void crearEventoConCuerpoSinTitleDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/calendar/events")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"ESPONTANEO","startsAt":"2026-09-01T19:00:00Z","locationType":"MEET"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(crearUseCase);
    }

    @Test
    void crearEventoSinNotifyOnCreateNiRemindByEmailUsaDefaultFalse() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        Evento respuesta = Evento.crear("Sesion", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), actorId,
                CLOCK);
        when(crearUseCase.crear(any())).thenReturn(new EventoVista(respuesta, null));

        mockMvc.perform(post("/api/v1/calendar/events")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sesion","eventType":"ESPONTANEO","startsAt":"2026-09-01T19:00:00Z",
                                 "locationType":"MEET","locationValue":"https://meet.google.com/abc"}
                                """))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CrearEventoCommand> captor = org.mockito.ArgumentCaptor.forClass(CrearEventoCommand.class);
        verify(crearUseCase).crear(captor.capture());
        assertThat(captor.getValue().notificarAlCrear()).isFalse();
        assertThat(captor.getValue().recordarPorEmail()).isFalse();
    }

    /**
     * El controller hace {@code ZoneId.of(r.timezone())} sobre texto que manda el cliente.
     * Una zona inexistente lanza {@code ZoneRulesException}, que NO es
     * {@code DateTimeParseException} ni {@code IllegalArgumentException}, asi que se escapaba
     * de los dos handlers que ya existian y salia como 500 con stacktrace. Ver E-38 en la
     * bitacora: la primera correccion capturo solo la subclase de parseo y dejo esta hermana
     * abierta.
     */
    @Test
    void crearEventoConTimezoneInexistenteDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/calendar/events")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sesion","eventType":"ESPONTANEO","startsAt":"2026-09-01T19:00:00Z",
                                 "locationType":"MEET","locationValue":"https://meet.google.com/abc",
                                 "timezone":"America/Nolandia"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(crearUseCase);
    }
}
