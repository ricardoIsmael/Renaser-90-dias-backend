package com.renaser.os.users.infrastructure.adapter.in.rest.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FaseDelPrograma;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FormacionDeLaSesion;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.OpcionDeFormacion;
import com.renaser.os.users.application.ports.in.formacion.ElegirFormacionActivaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La formación de la sesión por REST (spec 022): lo que la app lee al volver al frente y lo que el personal con varias formaciones
 * cambia desde Yo. La validación corta antes del caso de uso y cada rechazo llega con su código.
 */
@WebMvcTest(FormacionDeLaSesionController.class)
@AutoConfigureMockMvc(addFilters = false)
class FormacionDeLaSesionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarFormacionDeLaSesionUseCase consultar;
    @MockitoBean
    private ElegirFormacionActivaUseCase elegir;

    private final String actor = UserId.of(UUID.randomUUID()).toString();

    private static FormacionDeLaSesion sesion() {
        OpcionDeFormacion escuela = new OpcionDeFormacion("ESCUELA", "Escuela Renaser");
        OpcionDeFormacion diplomado = new OpcionDeFormacion("DIPLOMADO", "Diplomado Renaser");
        return new FormacionDeLaSesion(diplomado, List.of(escuela, diplomado), true, false, false, "DIPLOMADO", 365,
                List.of(new FaseDelPrograma(1, 1, 28), new FaseDelPrograma(2, 29, 137)));
    }

    @Test
    @DisplayName("GET: la formación activa, entre cuáles elige, la duración y las fases estiradas")
    void leeLaSesion() throws Exception {
        when(consultar.consultar(any())).thenReturn(sesion());

        mockMvc.perform(get("/api/v1/me/formacion").header("X-Actor-Id", actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa.codigo").value("DIPLOMADO"))
                .andExpect(jsonPath("$.disponibles.length()").value(2))
                .andExpect(jsonPath("$.puedeElegir").value(true))
                .andExpect(jsonPath("$.duracionDias").value(365))
                .andExpect(jsonPath("$.fases[1].primerDia").value(29));
    }

    @Test
    @DisplayName("PUT activa: elige la formación (en cualquier mayúscula) y responde la sesión nueva")
    void eligeLaActiva() throws Exception {
        when(elegir.elegir(any(), any())).thenReturn(sesion());

        mockMvc.perform(put("/api/v1/me/formacion/activa").header("X-Actor-Id", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"diplomado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa.nombre").value("Diplomado Renaser"));

        verify(elegir).elegir(any(), org.mockito.ArgumentMatchers.eq(Formacion.DIPLOMADO));
    }

    @Test
    @DisplayName("PUT activa: sin código o con una formación que no existe es 400 y no llega al caso de uso")
    void validaElCodigo() throws Exception {
        mockMvc.perform(put("/api/v1/me/formacion/activa").header("X-Actor-Id", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/me/formacion/activa").header("X-Actor-Id", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/me/formacion/activa").header("X-Actor-Id", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"MAESTRIA\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(elegir);
    }

    @Test
    @DisplayName("PUT activa: 403 a quien no trabaja en esa formación (un aprendiz, Administración o una que no tiene asignada)")
    void rechazaLaQueNoEsSuya() throws Exception {
        when(elegir.elegir(any(), any())).thenThrow(new NotAuthorizedException("No tienes asignada esa formación."));

        mockMvc.perform(put("/api/v1/me/formacion/activa").header("X-Actor-Id", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"CERTIFICACION\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No tienes asignada")));
    }
}
