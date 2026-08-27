package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gap #5: cubre §0.3 (autorizacion negativa) y las reglas de negocio portadas del backend
 * viejo (confirmacion exacta, idempotencia, purga con fallos parciales).
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final int DIAS_DE_GRACIA = 14;
    private static final Clock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private DeleteUserPort deleteUserPort;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                new RequireActiveUserGuard(loadUserPort), CLOCK, DIAS_DE_GRACIA);
        org.mockito.Mockito.lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User activo(UserId id) {
        return User.rehydrate(id, new Email("test" + id.value() + "@renaser.dev"), UserRole.TRAINEE,
                UserStatus.ACTIVE, "Test", null, null, null, null);
    }

    private static User suspendido(UserId id) {
        User user = activo(id);
        user.suspend();
        return user;
    }

    @Test
    @DisplayName("request rechaza una confirmacion que no sea exactamente ELIMINAR")
    void requestRechazaConfirmacionIncorrecta() {
        UserId userId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.request(new RequestAccountDeletionCommand(userId, "eliminar")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede pedir su propia baja")
    void requestRechazaActorSuspendido() {
        UserId userId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(suspendido(userId)));

        assertThatThrownBy(() -> service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("request con confirmacion correcta marca bajaSolicitadaEn y devuelve el estado")
    void requestMarcaLaBaja() {
        UserId userId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(activo(userId)));

        var estado = service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR"));

        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(estado.solicitadaEn()).isEqualTo(CLOCK.now());
        assertThat(estado.diasDeGracia()).isEqualTo(DIAS_DE_GRACIA);
        verify(saveUserPort).save(any());
    }

    @Test
    @DisplayName("request es idempotente: pedirla dos veces no reinicia el contador")
    void requestEsIdempotente() {
        UserId userId = UserId.of(UUID.randomUUID());
        User user = activo(userId);
        user.solicitarBaja(FixedClock.at(Instant.parse("2026-08-20T10:00:00Z")));
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(user));

        var estado = service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR"));

        assertThat(estado.solicitadaEn()).isEqualTo(Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    @DisplayName("cancel deshace la baja y el usuario vuelve a sinSolicitud")
    void cancelDeshaceLaBaja() {
        UserId userId = UserId.of(UUID.randomUUID());
        User user = activo(userId);
        user.solicitarBaja(CLOCK);
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(user));

        var estado = service.cancel(userId);

        assertThat(estado.bajaPendiente()).isFalse();
        verify(saveUserPort).save(any());
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede cancelar su baja")
    void cancelRechazaActorSuspendido() {
        UserId userId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(suspendido(userId)));

        assertThatThrownBy(() -> service.cancel(userId)).isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("status refleja el estado actual sin mutar nada")
    void statusNoMuta() {
        UserId userId = UserId.of(UUID.randomUUID());
        User user = activo(userId);
        user.solicitarBaja(CLOCK);
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(user));

        var estado = service.status(userId);

        assertThat(estado.bajaPendiente()).isTrue();
        verify(saveUserPort, never()).save(any());
    }

    // ─── purgeExpired ───────────────────────────────────────────────────────

    @Test
    @DisplayName("purgeExpired borra cada candidata y cuenta purgadas/fallidas por separado")
    void purgeExpiredCuentaPurgadasYFallidasPorSeparado() {
        UserId ok1 = UserId.of(UUID.randomUUID());
        UserId ok2 = UserId.of(UUID.randomUUID());
        UserId falla = UserId.of(UUID.randomUUID());
        when(loadUserPort.pendingDeletionUpTo(any())).thenReturn(List.of(ok1, ok2, falla));
        // lenient(): sin esto, Mockito en modo estricto (default de @ExtendWith(MockitoExtension))
        // no distingue "sin stub" de "stub para otro argumento" en un metodo void — al ver que
        // deleteById tiene un stub especifico para `falla`, cualquier otra invocacion (ok1, ok2)
        // la trata como sospechosa y lanza PotentialStubbingProblem en vez de dejarla pasar en
        // silencio (que es exactamente lo que este test necesita simular). No es un bug de
        // AccountDeletionService — la logica de produccion ya era correcta.
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("fk inesperada"))
                .when(deleteUserPort).deleteById(falla);

        var resultado = service.purgeExpired();

        assertThat(resultado.purgadas()).isEqualTo(2);
        assertThat(resultado.fallidas()).isEqualTo(1);
        verify(deleteUserPort, times(3)).deleteById(any());
    }

    @Test
    @DisplayName("purgeExpired usa el corte correcto: ahora menos los dias de gracia")
    void purgeExpiredUsaElCorteCorrecto() {
        when(loadUserPort.pendingDeletionUpTo(any())).thenReturn(List.of());

        service.purgeExpired();

        verify(loadUserPort).pendingDeletionUpTo(CLOCK.now().minus(DIAS_DE_GRACIA, java.time.temporal.ChronoUnit.DAYS));
    }

    /** Test de seguridad (CLAUDE.MD §0.3, adaptado): el comando SOLO tiene {@code userId} —
     * no existe forma de que el cliente pida la baja de OTRA cuenta, porque no hay ningun
     * campo de "usuario objetivo" que el compilador permita mandar (self-only por diseño,
     * mismo criterio que {@code UpdateMyProfileCommand}). */
    @Test
    @DisplayName("el comando de baja es self-only: solo tiene userId + confirmacion, nunca un id de otro usuario")
    void requestCommandEsSelfOnly() {
        assertThat(RequestAccountDeletionCommand.class.getRecordComponents()).extracting("name")
                .containsExactly("userId", "confirmacion");
    }

    /** Idem para cancel/status: reciben directamente el UserId del actor (via X-Actor-Id en
     * el controller), no un comando con un campo "targetUserId" que pudiera apuntar a otro. */
    @Test
    @DisplayName("cancel y status son self-only: reciben un UserId, nunca un comando con id de otro usuario")
    void cancelYStatusRecibenSoloElActor() throws NoSuchMethodException {
        var cancelMethod = com.renaser.os.users.application.ports.in.user.CancelAccountDeletionUseCase.class
                .getMethod("cancel", UserId.class);
        var statusMethod = com.renaser.os.users.application.ports.in.user.GetAccountDeletionStatusUseCase.class
                .getMethod("status", UserId.class);

        assertThat(cancelMethod.getParameterCount()).isEqualTo(1);
        assertThat(statusMethod.getParameterCount()).isEqualTo(1);
    }
}
