package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.IdentidadYaVinculadaException;
import com.renaser.os.shared.domain.SesionNoIniciadaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase.VincularIdentidadSocialCommand;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/auth/social/link} (docs/MODULO_AUTH.md §6.9). Cubre sobre todo la frontera
 * de autorizacion: <b>de donde sale el usuario que vincula</b>. La prueba que mas importa de
 * este archivo es {@link #headerXActorIdSoloNoAlcanzaParaVincular()}.
 */
@WebMvcTest(AutenticacionController.class)
@AutoConfigureMockMvc(addFilters = false)
class VincularIdentidadSocialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IniciarSesionUseCase iniciarSesionUseCase;
    @MockitoBean
    private GetMyProfileUseCase getMyProfileUseCase;
    @MockitoBean
    private SesionWebAdapter sesionWeb;
    @MockitoBean
    private SolicitarResetContrasenaUseCase solicitarResetContrasenaUseCase;
    @MockitoBean
    private ConfirmarResetContrasenaUseCase confirmarResetContrasenaUseCase;
    @MockitoBean
    private IniciarSesionConProveedorUseCase iniciarSesionConProveedorUseCase;
    @MockitoBean
    private VincularIdentidadSocialUseCase vincularIdentidadSocialUseCase;
    /** El controller lo inyecta desde §6.10; sus propias pruebas viven en {@code CompletarRegistroSocialControllerTest}. */
    @MockitoBean
    private CompletarRegistroSocialUseCase completarRegistroSocialUseCase;

    private static final String CUERPO = """
            {"proveedor":"GOOGLE","code":"un-code","codeVerifier":"un-verifier",
             "redirectUri":"https://app.renaser.dev/callback"}
            """;

    @Test
    @DisplayName("con sesion valida: 204 y el actor del comando sale de la sesion")
    void conSesionValidaDevuelve204YUsaElActorDeLaSesion() throws Exception {
        UserId actorDeLaSesion = UserId.of(UUID.randomUUID());
        when(sesionWeb.actorActual()).thenReturn(actorDeLaSesion);

        mockMvc.perform(post("/api/v1/auth/social/link").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNoContent());

        ArgumentCaptor<VincularIdentidadSocialCommand> comando =
                ArgumentCaptor.forClass(VincularIdentidadSocialCommand.class);
        verify(vincularIdentidadSocialUseCase).vincular(comando.capture());
        assertThat(comando.getValue().actorId()).isEqualTo(actorDeLaSesion);
        assertThat(comando.getValue().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
    }

    @Test
    @DisplayName("sin sesion: 401 y el caso de uso ni se entera")
    void sinSesionDevuelve401() throws Exception {
        when(sesionWeb.actorActual()).thenThrow(new SesionNoIniciadaException());

        mockMvc.perform(post("/api/v1/auth/social/link").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(vincularIdentidadSocialUseCase);
    }

    /**
     * <b>La prueba de seguridad central de este endpoint.</b> El resto de la API todavia acepta
     * {@code X-Actor-Id} como respaldo mientras dura la fase 4 de la migracion
     * (docs/MODULO_AUTH.md §8) — este endpoint NO puede aceptarlo: si lo hiciera, cualquiera
     * mandaria el UUID de otra persona y colgaria su cuenta de Google del usuario ajeno, que es
     * exactamente el agujero que este endpoint viene a cerrar. Sin sesion no hay vinculacion,
     * venga el header que venga.
     */
    @Test
    @DisplayName("X-Actor-Id solo NO alcanza para vincular: sin sesion es 401 igual")
    void headerXActorIdSoloNoAlcanzaParaVincular() throws Exception {
        when(sesionWeb.actorActual()).thenThrow(new SesionNoIniciadaException());

        mockMvc.perform(post("/api/v1/auth/social/link")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(vincularIdentidadSocialUseCase);
    }

    @Test
    @DisplayName("la identidad ya vinculada a otro usuario devuelve 409")
    void identidadDeOtroUsuarioDevuelve409() throws Exception {
        when(sesionWeb.actorActual()).thenReturn(UserId.of(UUID.randomUUID()));
        doThrow(new IdentidadYaVinculadaException("GOOGLE"))
                .when(vincularIdentidadSocialUseCase).vincular(any());

        mockMvc.perform(post("/api/v1/auth/social/link").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("si el proveedor rechaza el code: 401, igual que en el login social")
    void proveedorInvalidoDevuelve401() throws Exception {
        when(sesionWeb.actorActual()).thenReturn(UserId.of(UUID.randomUUID()));
        doThrow(new IdentidadProveedorInvalidaException("GOOGLE"))
                .when(vincularIdentidadSocialUseCase).vincular(any());

        mockMvc.perform(post("/api/v1/auth/social/link").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin code: 400 y no llega al caso de uso")
    void sinCodeDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/link").contentType(MediaType.APPLICATION_JSON).content("""
                        {"proveedor":"GOOGLE","code":"","codeVerifier":"un-verifier",
                         "redirectUri":"https://app.renaser.dev/callback"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vincularIdentidadSocialUseCase);
    }
}
