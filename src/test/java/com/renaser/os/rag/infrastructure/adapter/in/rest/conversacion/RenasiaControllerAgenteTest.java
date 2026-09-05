package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase.PaginaMensajesRenasia;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase.PreguntarRenasiaCommand;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COMPANION;
import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COURSE_TUTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de D-102 contra el controller real, con los filtros de seguridad apagados (la
 * autenticacion la prueba {@link RenasiaControllerAutenticacionTest}; aca importa el mapeo del
 * campo {@code agent}). Lo que se verifica es lo que una app vieja o un cliente descuidado
 * podrian romper: sin {@code agent} se habla con el acompanante, con {@code COURSE_TUTOR} viajan
 * curso y ambito, y un agente desconocido es 400 en vez de caer en silencio en uno de los dos.
 */
@WebMvcTest(RenasiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RenasiaControllerAgenteTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";
    private static final String RUTA = "/api/v1/renasia/mensajes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private PreguntarRenasiaUseCase preguntarUseCase;
    @MockitoBean
    private ObtenerHistorialUseCase obtenerHistorialUseCase;

    private UUID actorId;

    @BeforeEach
    void actorActivo() {
        actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(preguntarUseCase.preguntar(any())).thenReturn(Flux.just(new EventoRenasia.Fin()));
        when(obtenerHistorialUseCase.obtenerHistorial(any(), any(), any(), anyInt()))
                .thenReturn(new PaginaMensajesRenasia(List.of(), null, false));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private PreguntarRenasiaCommand comandoRecibido() {
        ArgumentCaptor<PreguntarRenasiaCommand> captor = ArgumentCaptor.forClass(PreguntarRenasiaCommand.class);
        verify(preguntarUseCase).preguntar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("sin `agent` (cliente anterior a D-102) se habla con el acompanante, y el scope no se arrastra")
    void sinAgentEsElAcompanante() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header(HEADER_ACTOR_ID, actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hola\",\"scope\":\"el curso X\"}"))
                .andExpect(status().isOk());

        PreguntarRenasiaCommand comando = comandoRecibido();
        assertThat(comando.agente()).isEqualTo(COMPANION);
        assertThat(comando.pregunta()).isEqualTo("hola");
        assertThat(comando.ambito()).isNull();
        assertThat(comando.cursoId()).isNull();
    }

    @Test
    @DisplayName("con `agent=COURSE_TUTOR` viajan el curso y el ambito hasta el comando")
    void conCourseTutorViajanCursoYAmbito() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header(HEADER_ACTOR_ID, actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"que dice la leccion?","agent":"COURSE_TUTOR",
                                 "courseId":"curso-1","scope":"el curso \\"X\\", leccion \\"Y\\""}
                                """))
                .andExpect(status().isOk());

        PreguntarRenasiaCommand comando = comandoRecibido();
        assertThat(comando.agente()).isEqualTo(COURSE_TUTOR);
        assertThat(comando.cursoId()).isEqualTo("curso-1");
        assertThat(comando.ambito()).isEqualTo("el curso \"X\", leccion \"Y\"");
    }

    @Test
    @DisplayName("un `agent` desconocido es 400, no cae en silencio en uno de los dos")
    void agentDesconocidoEs400() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header(HEADER_ACTOR_ID, actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hola\",\"agent\":\"GURU\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(preguntarUseCase);
    }

    @Test
    @DisplayName("el historial sin `agent` es el del acompanante")
    void historialSinAgentEsElAcompanante() throws Exception {
        mockMvc.perform(get(RUTA).header(HEADER_ACTOR_ID, actorId.toString()))
                .andExpect(status().isOk());

        verify(obtenerHistorialUseCase).obtenerHistorial(eq(UserId.of(actorId)), eq(COMPANION), isNull(), eq(30));
    }

    @Test
    @DisplayName("el historial con `agent=COURSE_TUTOR` es el de Sparkie, nunca mezclado")
    void historialPorAgente() throws Exception {
        mockMvc.perform(get(RUTA).param("agent", "COURSE_TUTOR").param("limit", "10")
                        .header(HEADER_ACTOR_ID, actorId.toString()))
                .andExpect(status().isOk());

        verify(obtenerHistorialUseCase).obtenerHistorial(eq(UserId.of(actorId)), eq(COURSE_TUTOR), isNull(), eq(10));
    }

    @Test
    @DisplayName("el historial con un `agent` desconocido es 400")
    void historialConAgentDesconocidoEs400() throws Exception {
        mockMvc.perform(get(RUTA).param("agent", "GURU").header(HEADER_ACTOR_ID, actorId.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(obtenerHistorialUseCase);
    }
}
