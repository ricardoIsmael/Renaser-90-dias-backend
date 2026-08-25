package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRequestTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static AccountRequest pendingRequest() {
        return AccountRequest.submit(newUserId(), new Email("aspirante@renaser.com"), "Ana Aspirante",
                "+51999999999", "Lima", "203.0.113.5", CLOCK);
    }

    private static User adminActor() {
        return User.rehydrate(newUserId(), new Email("admin@renaser.com"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin", null, null, null, null);
    }

    private static User traineeActor() {
        return User.registerTrainee(newUserId(), new Email("aprendiz@renaser.com"), "Aprendiz");
    }

    private static UserId newUserId() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("toda solicitud nueva nace PENDING, sin campo role")
    void submitStartsPending() {
        AccountRequest request = pendingRequest();

        assertThat(request.status()).isEqualTo(AccountRequestStatus.PENDING);
        assertThat(request.rejectionReason()).isNull();
        assertThat(request.reviewedBy()).isNull();
        assertThat(request.createdUserId()).isNull();
    }

    @Test
    @DisplayName("un ADMIN puede aprobar una solicitud pendiente")
    void adminCanApprove() {
        AccountRequest request = pendingRequest();
        UserId createdUserId = newUserId();

        request.approve(adminActor(), createdUserId, CLOCK);

        assertThat(request.status()).isEqualTo(AccountRequestStatus.APPROVED);
        assertThat(request.createdUserId()).isEqualTo(createdUserId);
        assertThat(request.reviewedAt()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("un TRAINEE no puede aprobar solicitudes")
    void traineeCannotApprove() {
        AccountRequest request = pendingRequest();

        assertThatThrownBy(() -> request.approve(traineeActor(), newUserId(), CLOCK))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(request.status()).isEqualTo(AccountRequestStatus.PENDING);
    }

    @Test
    @DisplayName("rechazar exige un motivo, nunca queda mudo (CHECK rechazo_con_motivo del SQL)")
    void rejectRequiresReason() {
        AccountRequest request = pendingRequest();

        assertThatThrownBy(() -> request.reject(adminActor(), "  ", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un ADMIN puede rechazar con motivo")
    void adminCanReject() {
        AccountRequest request = pendingRequest();

        request.reject(adminActor(), "Datos incompletos", CLOCK);

        assertThat(request.status()).isEqualTo(AccountRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEqualTo("Datos incompletos");
    }

    @Test
    @DisplayName("una solicitud ya decidida no se puede volver a decidir")
    void cannotDecideTwice() {
        AccountRequest request = pendingRequest();
        request.approve(adminActor(), newUserId(), CLOCK);

        assertThatThrownBy(() -> request.reject(adminActor(), "tarde", CLOCK))
                .isInstanceOf(IllegalStateException.class);
    }
}
