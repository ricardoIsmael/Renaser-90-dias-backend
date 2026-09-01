package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre solo {@code POST /api/v1/auth/social} (docs/MODULO_AUTH.md §6.1, §6.10) — el resto de
 * {@link AutenticacionController} (password login/logout/me, reset de contrasena,
 * {@code /social/complete}) tiene sus propias pruebas en archivos separados para no competir por
 * el mismo path de test.
 */
@WebMvcTest(AutenticacionController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginSocialControllerTest {

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
    /** El controller lo inyecta desde §6.9; sus propias pruebas viven en {@code VincularIdentidadSocialControllerTest}. */
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
    void identidadExistenteEstableceSesionYDevuelve200ConElPerfil() throws Exception {
        UserId id = UserId.of(UUID.randomUUID());
        User usuario = User.rehydrate(id, new Email("actor@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE,
                "Actor de Prueba", null, null, null, null);
        when(iniciarSesionConProveedorUseCase.iniciarSesion(any()))
                .thenReturn(new ResultadoLoginSocial.SesionIniciada(usuario));

        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(sesionWeb).establecer(org.mockito.ArgumentMatchers.eq(id), any(), any());
    }

    /**
     * D-65 (2026-09-01, docs/MODULO_AUTH.md §6.10): identidad nueva ya NO abre una
     * AccountRequest en esta llamada — devuelve el token de continuacion y los datos para
     * prellenar el formulario de confirmacion.
     */
    @Test
    void identidadNuevaDevuelve202ConElTokenDeContinuacionYNoEstableceSesion() throws Exception {
        when(iniciarSesionConProveedorUseCase.iniciarSesion(any()))
                .thenReturn(new ResultadoLoginSocial.RegistroPendiente("token-registro-pendiente",
                        "nuevo@renaser.dev", "Persona Nueva"));

        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.registroPendienteToken").value("token-registro-pendiente"))
                .andExpect(jsonPath("$.email").value("nuevo@renaser.dev"))
                .andExpect(jsonPath("$.fullName").value("Persona Nueva"));

        verifyNoInteractions(sesionWeb);
        verifyNoInteractions(completarRegistroSocialUseCase);
    }

    /**
     * A-7: "tu solicitud sigue en revision" NO es un error. Antes caia en el mismo 409 generico
     * que todo lo que no fuera sesion, y la app no tenia forma de mostrarle a la persona que ya
     * estaba registrada y solo faltaba que la aprobaran.
     */
    @Test
    void solicitudPendienteDevuelve202EnRevisionYNoUnError() throws Exception {
        AccountRequestId solicitudId = AccountRequestId.of(UUID.randomUUID());
        when(iniciarSesionConProveedorUseCase.iniciarSesion(any()))
                .thenReturn(new ResultadoLoginSocial.SolicitudEnRevision(solicitudId));

        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accountRequestId").value(solicitudId.value().toString()))
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        verifyNoInteractions(sesionWeb);
    }

    /**
     * Un correo que ya tiene cuenta pero sin vinculo social: 409 y, sobre todo, <b>sin sesion</b>.
     * Vincular por coincidencia de correo es como alguien se apodera de una cuenta ajena (§6.4).
     */
    @Test
    void cuentaExistenteSinVinculoDevuelve409YNoEstableceSesion() throws Exception {
        when(iniciarSesionConProveedorUseCase.iniciarSesion(any()))
                .thenReturn(new ResultadoLoginSocial.CuentaExistenteSinVinculo(ProveedorIdentidad.GOOGLE));

        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(sesionWeb);
    }

    @Test
    void proveedorInvalidoDevuelve401() throws Exception {
        when(iniciarSesionConProveedorUseCase.iniciarSesion(any()))
                .thenThrow(new IdentidadProveedorInvalidaException("GOOGLE"));

        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinCodeDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content("""
                        {"proveedor":"GOOGLE","code":"","codeVerifier":"un-verifier",
                         "redirectUri":"https://app.renaser.dev/callback"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(iniciarSesionConProveedorUseCase);
    }

    @Test
    void sinProveedorDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social").contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"un-code","codeVerifier":"un-verifier",
                         "redirectUri":"https://app.renaser.dev/callback"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(iniciarSesionConProveedorUseCase);
    }
}
