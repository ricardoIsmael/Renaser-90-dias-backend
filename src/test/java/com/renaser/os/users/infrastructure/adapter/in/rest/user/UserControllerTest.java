package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.CancelAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase.ConfirmarAvatarCommand;
import com.renaser.os.users.application.ports.in.user.GetAccountDeletionStatusUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase.MyProfile;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase.TraineeProfileSummary;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase.SolicitarUrlAvatarCommand;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase.UrlAvatar;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyFullProfileUseCase getMyFullProfileUseCase;
    @MockitoBean
    private UpdateMyProfileUseCase updateMyProfileUseCase;
    @MockitoBean
    private InviteAndCreateUserUseCase inviteUseCase;
    @MockitoBean
    private UpdateUserRoleUseCase updateUserRoleUseCase;
    @MockitoBean
    private SolicitarUrlAvatarUseCase solicitarUrlAvatarUseCase;
    @MockitoBean
    private ConfirmarAvatarUseCase confirmarAvatarUseCase;
    @MockitoBean
    private RequestAccountDeletionUseCase requestAccountDeletionUseCase;
    @MockitoBean
    private CancelAccountDeletionUseCase cancelAccountDeletionUseCase;
    @MockitoBean
    private GetAccountDeletionStatusUseCase getAccountDeletionStatusUseCase;

    private static User activo(UserId id) {
        return User.rehydrate(id, new Email("test" + id.value() + "@renaser.dev"), UserRole.TRAINEE,
                UserStatus.ACTIVE, "Test", null, null, null, null);
    }

    // ─── hueco #1: perfil enriquecido ───────────────────────────────────────

    @Test
    void meIncluyeTraineeProfileCuandoElActorEstaInscripto() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        var resumen = new TraineeProfileSummary("Correr una maraton", CLOCK.today(), "PHYSICAL", false, null, 0);
        when(getMyFullProfileUseCase.getMyFullProfile(actorId)).thenReturn(new MyProfile(activo(actorId), resumen));

        mockMvc.perform(post("/api/v1/users/me").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traineeProfile.personalChallengeName").value("Correr una maraton"))
                .andExpect(jsonPath("$.traineeProfile.goalType").value("PHYSICAL"))
                .andExpect(jsonPath("$.traineeProfile.isProgramCompleted").value(false));
    }

    @Test
    void meSinParticipacionNoIncluyeTraineeProfile() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(getMyFullProfileUseCase.getMyFullProfile(actorId)).thenReturn(new MyProfile(activo(actorId), null));

        mockMvc.perform(post("/api/v1/users/me").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traineeProfile").doesNotExist());
    }

    @Test
    void meComoSuspendidoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(getMyFullProfileUseCase.getMyFullProfile(actorId))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mockMvc.perform(post("/api/v1/users/me").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    // ─── gap #4: avatar generico ────────────────────────────────────────────

    @Test
    void urlDeSubidaAvatarDevuelveBucketYRuta() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(solicitarUrlAvatarUseCase.solicitarUrl(new SolicitarUrlAvatarCommand(actorId, "image/png")))
                .thenReturn(new UrlAvatar(URI.create("https://s3.example/avatares/" + actorId), "renaser-files",
                        "avatares/" + actorId));

        mockMvc.perform(post("/api/v1/users/me/avatar/upload-url")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoContenido\":\"image/png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("renaser-files"))
                .andExpect(jsonPath("$.ruta").value("avatares/" + actorId));
    }

    @Test
    void confirmarAvatarDevuelve204() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/users/me/avatar")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"renaser-files\",\"ruta\":\"avatares/" + actorId + "\"}"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(confirmarAvatarUseCase).confirmar(
                new ConfirmarAvatarCommand(actorId, "renaser-files", "avatares/" + actorId));
    }

    // ─── gap #5: baja de cuenta ─────────────────────────────────────────────

    @Test
    void estadoBajaCuentaDevuelveSinSolicitud() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(getAccountDeletionStatusUseCase.status(actorId)).thenReturn(EstadoBajaCuenta.sinSolicitud(14));

        mockMvc.perform(get("/api/v1/users/me/account-deletion").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaPendiente").value(false))
                .andExpect(jsonPath("$.diasDeGracia").value(14));
    }

    @Test
    void solicitarBajaCuentaConConfirmacionCorrectaDevuelveElEstado() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(requestAccountDeletionUseCase.request(new RequestAccountDeletionCommand(actorId, "ELIMINAR")))
                .thenReturn(EstadoBajaCuenta.de(CLOCK.now(), CLOCK.now(), 14));

        mockMvc.perform(post("/api/v1/users/me/account-deletion")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmacion\":\"ELIMINAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaPendiente").value(true))
                .andExpect(jsonPath("$.diasRestantes").value(14));
    }

    @Test
    void solicitarBajaCuentaConConfirmacionIncorrectaDevuelve400() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(requestAccountDeletionUseCase.request(any()))
                .thenThrow(new IllegalArgumentException("CONFIRMATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/users/me/account-deletion")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmacion\":\"borrar\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solicitarBajaCuentaComoSuspendidoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(requestAccountDeletionUseCase.request(any())).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mockMvc.perform(post("/api/v1/users/me/account-deletion")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmacion\":\"ELIMINAR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelarBajaCuentaDevuelveElEstadoSinSolicitud() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(cancelAccountDeletionUseCase.cancel(actorId)).thenReturn(EstadoBajaCuenta.sinSolicitud(14));

        mockMvc.perform(delete("/api/v1/users/me/account-deletion").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaPendiente").value(false));
    }
}
