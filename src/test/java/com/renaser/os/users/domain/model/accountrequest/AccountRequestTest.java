package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
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
        return AccountRequest.submit(newRequestId(), newUserId(), new Email("aspirante@renaser.com"),
                "Ana Aspirante",
                "+51999999999", "Lima", "203.0.113.5", null, CLOCK);
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

    private static AccountRequestId newRequestId() {
        return AccountRequestId.of(UUID.randomUUID());
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

    /**
     * D-61 (2026-09-01). Antes de esta fecha el invariante era el contrario — {@code submit}
     * exigia telefono y explotaba con "El telefono no puede ser vacio" — y por eso el alta con
     * {@code phone: null}, que es lo que el frontend manda hoy, terminaba en 400. El dato pasa a
     * pedirse en la Ficha Inicial del onboarding.
     */
    @Test
    @DisplayName("D-61: una solicitud sin telefono es valida y queda con telefono null")
    void submitAceptaTelefonoNull() {
        AccountRequest request = AccountRequest.submit(newRequestId(), newUserId(),
                new Email("sintelefono@renaser.com"), "Ana Sin Telefono",
                null, "Lima", "203.0.113.5", null, CLOCK);

        assertThat(request.phone()).isNull();
        assertThat(request.status()).isEqualTo(AccountRequestStatus.PENDING);
    }

    /**
     * "No lo dio" tiene que ser UN solo valor en la columna. Si un telefono en blanco se guardara
     * como {@code ""}, cada consulta futura tendria que preguntar por null y por vacio.
     */
    @Test
    @DisplayName("D-61: un telefono en blanco se normaliza a null, no se guarda como cadena vacia")
    void submitNormalizaTelefonoEnBlancoANull() {
        AccountRequest request = AccountRequest.submit(newRequestId(), newUserId(),
                new Email("blanco@renaser.com"), "Ana Blanco",
                "   ", "Lima", "203.0.113.5", null, CLOCK);

        assertThat(request.phone()).isNull();
    }

    @Test
    @DisplayName("D-61: si el telefono viene, se sigue guardando (solo dejo de ser obligatorio)")
    void submitConservaElTelefonoCuandoViene() {
        assertThat(pendingRequest().phone()).isEqualTo("+51999999999");
    }

    @Test
    @DisplayName("A-7: una solicitud por formulario no tiene origen social, y eso es valido")
    void unaSolicitudPorFormularioNoTieneOrigenSocial() {
        assertThat(pendingRequest().origenSocial()).isNull();
    }

    @Test
    @DisplayName("A-7: el origen social sobrevive intacto hasta la aprobacion, que es cuando se "
            + "usa para crear la IdentidadExterna")
    void elOrigenSocialSobreviveHastaLaAprobacion() {
        OrigenSocial origen = new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-1");
        AccountRequest request = AccountRequest.submit(newRequestId(), newUserId(),
                new Email("social@renaser.com"),
                "Sofia Social", "+51999999999", "Lima", "203.0.113.5", origen, CLOCK);

        request.approve(adminActor(), newUserId(), CLOCK);

        assertThat(request.origenSocial()).isEqualTo(origen);
    }

    @Test
    @DisplayName("proveedor y sujeto viajan juntos: uno sin el otro no identifica a nadie")
    void elOrigenSocialExigeLosDosCampos() {
        assertThatThrownBy(() -> new OrigenSocial(ProveedorIdentidad.GOOGLE, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrigenSocial(null, "google-sub-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("el sujeto del proveedor es un identificador correlacionable: nunca al log")
    void elOrigenSocialNoFiltraElSujetoEnElToString() {
        assertThat(new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-secreto").toString())
                .doesNotContain("google-sub-secreto");
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
