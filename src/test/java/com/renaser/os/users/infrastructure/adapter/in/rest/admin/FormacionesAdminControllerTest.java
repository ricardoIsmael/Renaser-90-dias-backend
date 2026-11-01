package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase.AsignarFormacionesCommand;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase.CambiarDuracionCommand;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase.ConfigurarFormacionCommand;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeUnaPersonaUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeUnaPersonaUseCase.FormacionDeUnaPersona;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato REST de lo que Administración hace con las formaciones (spec 022): configurar una, asignar las del personal, trasladar a un
 * aprendiz y mirar la de una persona. Lo que se prueba aquí es la capa web: la forma de lo que entra y sale, que la validación corta
 * ANTES del caso de uso y que cada rechazo del caso de uso llega con su código (403 a quien no le toca, 409 por versión o por regla, 404).
 * El guard real de Administración vive en los servicios y se prueba contra Postgres en {@code CentroDeFormacionesIT}.
 */
@WebMvcTest(FormacionesAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class FormacionesAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigurarFormacionUseCase configurar;
    @MockitoBean
    private AsignarFormacionesDelPersonalUseCase asignar;
    @MockitoBean
    private TrasladarAprendizDeFormacionUseCase trasladar;
    @MockitoBean
    private ConsultarFormacionDeUnaPersonaUseCase consultarPersona;
    @MockitoBean
    private CambiarDuracionDelProgramaUseCase cambiarDuracion;

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UUID persona = UUID.randomUUID();

    private MockHttpServletRequestBuilder conActor(MockHttpServletRequestBuilder pedido, String cuerpo) {
        MockHttpServletRequestBuilder armado = pedido.header("X-Actor-Id", actor.toString());
        return cuerpo == null ? armado : armado.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static final String CONFIGURAR_OK =
            "{\"nombre\":\"Diplomado Plus\",\"descripcion\":\"Tres meses o un año\",\"activa\":true,\"duracionesDias\":[90,365],\"version\":3}";

    // ---------------------------------------------------------------------------------------------------------- PUT /formaciones/{codigo}

    @Test
    @DisplayName("configurar: manda el comando con lo que llegó y responde la formación ya guardada")
    void configurarResponde200() throws Exception {
        when(configurar.configurar(any())).thenReturn(new FormacionConfigurada(Formacion.DIPLOMADO, "Diplomado Plus", "Tres meses o un año", true, List.of(90, 365), 4));

        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/diplomado"), CONFIGURAR_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("DIPLOMADO"))
                .andExpect(jsonPath("$.nombre").value("Diplomado Plus"))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.etiquetasDeDuracion[1]").value("1 año"));

        ArgumentCaptor<ConfigurarFormacionCommand> comando = ArgumentCaptor.forClass(ConfigurarFormacionCommand.class);
        verify(configurar).configurar(comando.capture());
        assertThat(comando.getValue().actor()).isEqualTo(actor);
        assertThat(comando.getValue().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(comando.getValue().duracionesDias()).containsExactly(90, 365);
        assertThat(comando.getValue().version()).isEqualTo(3);
    }

    @Test
    @DisplayName("configurar: sin nombre, sin duraciones o con una formación que no existe es 400 y no llega al caso de uso")
    void configurarValida() throws Exception {
        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/DIPLOMADO"),
                        "{\"nombre\":\" \",\"activa\":true,\"duracionesDias\":[90],\"version\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/DIPLOMADO"),
                        "{\"nombre\":\"Diplomado\",\"activa\":true,\"duracionesDias\":[],\"version\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/MAESTRIA"), CONFIGURAR_OK)).andExpect(status().isBadRequest());

        verifyNoInteractions(configurar);
    }

    @Test
    @DisplayName("configurar: 403 a quien no es Administración y 409 si otra persona la cambió antes")
    void configurarRechazos() throws Exception {
        when(configurar.configurar(any()))
                .thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST"))
                .thenThrow(new IllegalStateException("Otra persona cambió esta formación mientras la editabas."));

        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/ESCUELA"), CONFIGURAR_OK)).andExpect(status().isForbidden());
        mockMvc.perform(conActor(put("/api/v1/admin/formaciones/ESCUELA"), CONFIGURAR_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Otra persona")));
    }

    // ---------------------------------------------------------------------------------------------------------- PUT /users/{id}/formaciones

    @Test
    @DisplayName("asignar: responde 204 y manda las formaciones y la activa; sin activa, la activa queda vacía")
    void asignarResponde204() throws Exception {
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), "{\"formaciones\":[\"ESCUELA\",\"diplomado\"],\"activa\":\"DIPLOMADO\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), "{\"formaciones\":[\"ESCUELA\"]}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<AsignarFormacionesCommand> comando = ArgumentCaptor.forClass(AsignarFormacionesCommand.class);
        verify(asignar, org.mockito.Mockito.times(2)).asignar(comando.capture());
        assertThat(comando.getAllValues().get(0).formaciones()).containsExactlyInAnyOrder(Formacion.ESCUELA, Formacion.DIPLOMADO);
        assertThat(comando.getAllValues().get(0).activa()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(comando.getAllValues().get(1).activa()).isNull();
        assertThat(comando.getAllValues().get(1).persona()).isEqualTo(UserId.of(persona));
    }

    @Test
    @DisplayName("asignar: la lista vacía o una formación que no existe es 400 y no llega al caso de uso")
    void asignarValida() throws Exception {
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), "{\"formaciones\":[]}")).andExpect(status().isBadRequest());
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), "{\"formaciones\":[\"MAESTRIA\"]}")).andExpect(status().isBadRequest());

        verifyNoInteractions(asignar);
    }

    @Test
    @DisplayName("asignar: 403 a quien no le toca, 409 si lidera un grupo en la que se le quita y 404 si la persona no existe")
    void asignarRechazos() throws Exception {
        doThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST")).doThrow(new IllegalStateException("No se puede quitar la formación «Diplomado Renaser»: esta persona lidera el grupo «Fénix»."))
                .doThrow(new NoSuchElementException("Usuario no encontrado")).when(asignar).asignar(any());
        String cuerpo = "{\"formaciones\":[\"ESCUELA\"]}";

        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), cuerpo)).andExpect(status().isForbidden());
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("lidera el grupo")));
        mockMvc.perform(conActor(put("/api/v1/admin/users/{id}/formaciones", persona), cuerpo)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------------------------- POST /trainees/{id}/formacion

    @Test
    @DisplayName("trasladar: responde 204 y manda destino, duración, si se la da por terminada y el motivo")
    void trasladarResponde204() throws Exception {
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona),
                        "{\"formacion\":\"DIPLOMADO\",\"duracionDias\":365,\"darPorTerminada\":true,\"motivo\":\"Cambió de programa\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<TrasladarCommand> comando = ArgumentCaptor.forClass(TrasladarCommand.class);
        verify(trasladar).trasladar(comando.capture());
        assertThat(comando.getValue().actor()).isEqualTo(actor);
        assertThat(comando.getValue().aprendiz()).isEqualTo(UserId.of(persona));
        assertThat(comando.getValue().destino()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(comando.getValue().duracionDias()).isEqualTo(365);
        assertThat(comando.getValue().darPorTerminada()).isTrue();
        assertThat(comando.getValue().motivo()).isEqualTo("Cambió de programa");
    }

    @Test
    @DisplayName("trasladar: sin formación, sin duración o con una formación que no existe es 400 y no llega al caso de uso")
    void trasladarValida() throws Exception {
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), "{\"duracionDias\":90}")).andExpect(status().isBadRequest());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), "{\"formacion\":\"DIPLOMADO\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), "{\"formacion\":\"MAESTRIA\",\"duracionDias\":90}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(trasladar);
    }

    @Test
    @DisplayName("trasladar: 403 a quien no le toca, 409 si todavía no terminó y 400 si la formación no ofrece esa duración")
    void trasladarRechazos() throws Exception {
        doThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST")).doThrow(new IllegalStateException("Esta persona todavía no terminó su formación."))
                .doThrow(new IllegalArgumentException("Esa formación no ofrece una duración de 365 días.")).when(trasladar).trasladar(any());
        String cuerpo = "{\"formacion\":\"CERTIFICACION\",\"duracionDias\":365}";

        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), cuerpo)).andExpect(status().isForbidden());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), cuerpo)).andExpect(status().isConflict());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/formacion", persona), cuerpo)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------------------- GET /users/{id}/formaciones

    @Test
    @DisplayName("la formación de una persona: 200 con su formación y duración, y 403 a quien no le toca")
    void formacionDeUnaPersona() throws Exception {
        when(consultarPersona.consultar(any(), any()))
                .thenReturn(new FormacionDeUnaPersona(Formacion.DIPLOMADO, 365, true, List.of(), List.of()))
                .thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST"));

        mockMvc.perform(conActor(get("/api/v1/admin/users/{id}/formaciones", persona), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formacion").value("DIPLOMADO"))
                .andExpect(jsonPath("$.duracionDias").value(365))
                .andExpect(jsonPath("$.historial").isEmpty());
        mockMvc.perform(conActor(get("/api/v1/admin/users/{id}/formaciones", persona), null)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------------------- POST /trainees/{id}/duracion (spec 025)

    @Test
    @DisplayName("cambiar la duración: responde 204 y manda la duración nueva y el motivo")
    void cambiarLaDuracionResponde204() throws Exception {
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), "{\"duracionDias\":365,\"motivo\":\"Pidió más tiempo\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<CambiarDuracionCommand> comando = ArgumentCaptor.forClass(CambiarDuracionCommand.class);
        verify(cambiarDuracion).cambiar(comando.capture());
        assertThat(comando.getValue().aprendiz()).isEqualTo(UserId.of(persona));
        assertThat(comando.getValue().duracionDias()).isEqualTo(365);
        assertThat(comando.getValue().motivo()).isEqualTo("Pidió más tiempo");
    }

    @Test
    @DisplayName("cambiar la duración: sin duración o sin motivo es 400 y no llega al caso de uso")
    void cambiarLaDuracionValida() throws Exception {
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), "{\"motivo\":\"x\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), "{\"duracionDias\":365,\"motivo\":\" \"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(cambiarDuracion);
    }

    @Test
    @DisplayName("cambiar la duración: 403 a quien no le toca, 409 si ya la tiene y 404 si la persona no existe")
    void cambiarLaDuracionRechazos() throws Exception {
        doThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST")).doThrow(new IllegalStateException("Esa persona ya tiene esa duración."))
                .doThrow(new NoSuchElementException("Participante no encontrado")).when(cambiarDuracion).cambiar(any());
        String cuerpo = "{\"duracionDias\":365,\"motivo\":\"Pidió más tiempo\"}";

        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), cuerpo)).andExpect(status().isForbidden());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), cuerpo)).andExpect(status().isConflict());
        mockMvc.perform(conActor(post("/api/v1/admin/trainees/{id}/duracion", persona), cuerpo)).andExpect(status().isNotFound());
    }
}
