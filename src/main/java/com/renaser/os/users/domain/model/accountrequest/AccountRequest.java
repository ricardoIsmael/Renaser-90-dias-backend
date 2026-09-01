package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Solicitud de alta por autoregistro (CLAUDE.MD §5.3.3, tabla `solicitudes_cuenta`
 * de docs/db/sql/BD_NUEVA_V1.sql — esa tabla es la fuente de verdad de estos campos,
 * no se inventa ninguno).
 *
 * A proposito NO tiene un campo `role`: es el mismo blindaje que User.registerTrainee(),
 * llevado a la solicitud misma. El cliente publico no puede pedir un rol porque
 * no existe el lugar donde ponerlo.
 *
 * El permiso para aprobar/rechazar reusa User.canManageRoles() (ADMIN/ALCHEMIST) porque
 * es el mismo conjunto de actores que docs/MODULOS_A_AVANZAR.md §4.1 ya adjudica a
 * APPROVE_ACCOUNT_REQUEST. Cuando exista el enum Permission (bloqueado por R-2: que
 * permisos tiene MENTOR_LEAD), esto pasa a actor.can(APPROVE_ACCOUNT_REQUEST).
 *
 * Lombok solo genera constructor privado + getters fluent + equals/hashCode por id
 * (mismo patron que User; ver CLAUDE.MD §5.4.5). La validacion vive en submit(), no en
 * el constructor ni en rehydrate() — igual que buckpal.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AccountRequest {

    private final AccountRequestId id;
    private final UserId usuarioId;
    private final Email email;
    private final String fullName;
    private final String phone;
    private final String city;
    /** null = alta por formulario. No null = alta abierta por un proveedor social (ver {@link OrigenSocial}). */
    private final OrigenSocial origenSocial;
    private AccountRequestStatus status;
    private String rejectionReason;
    private UserId reviewedBy;
    private Instant reviewedAt;
    private UserId createdUserId;
    private final String requestIp;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Alta publica. Sin campo role: ver javadoc de la clase.
     *
     * <p>{@code origenSocial} es null en el alta por formulario y no null cuando la abrio un
     * proveedor social. Guardarlo aca es lo que permite que {@link #approve} cree la
     * {@code IdentidadExterna}: el {@code sub} se verifica antes de esta llamada y no vuelve a
     * existir en ningun otro lado (A-7, docs/MODULO_AUTH.md §6.7).
     *
     * <p>{@code phone} es OPCIONAL desde el 2026-09-01 (D-61): el alta pide lo minimo y el
     * telefono se recoge en la Ficha Inicial del onboarding. Un telefono vacio se normaliza a
     * null en vez de guardarse como cadena en blanco — "todavia no lo dio" tiene que ser un solo
     * valor en la base, si no cada consulta necesita preguntar por los dos.
     */
    public static AccountRequest submit(AccountRequestId id, UserId usuarioId, Email email, String fullName,
                                         String phone, String city, String requestIp,
                                         OrigenSocial origenSocial, Clock clock) {
        Instant now = clock.now();
        return new AccountRequest(Objects.requireNonNull(id, "id es obligatorio"),
                Objects.requireNonNull(usuarioId, "usuarioId es obligatorio"),
                Objects.requireNonNull(email, "email es obligatorio"),
                requireNotBlank(fullName, "El nombre no puede ser vacio"),
                normalizarOpcional(phone), city, origenSocial,
                AccountRequestStatus.PENDING, null, null, null, null, requestIp, now, now);
    }

    /** Solo para el adaptador de persistencia: reconstruye una solicitud ya existente. */
    public static AccountRequest rehydrate(AccountRequestId id, UserId usuarioId, Email email,
                                            String fullName, String phone, String city,
                                            OrigenSocial origenSocial, AccountRequestStatus status,
                                            String rejectionReason, UserId reviewedBy, Instant reviewedAt,
                                            UserId createdUserId, String requestIp, Instant createdAt,
                                            Instant updatedAt) {
        return new AccountRequest(id, usuarioId, email, fullName, phone, city, origenSocial, status,
                rejectionReason, reviewedBy, reviewedAt, createdUserId, requestIp, createdAt, updatedAt);
    }

    public void approve(User actor, UserId createdUserId, Clock clock) {
        requireManager(actor);
        requirePending();
        this.status = AccountRequestStatus.APPROVED;
        this.reviewedBy = actor.id();
        this.createdUserId = Objects.requireNonNull(createdUserId, "createdUserId es obligatorio al aprobar");
        this.reviewedAt = clock.now();
        this.updatedAt = this.reviewedAt;
    }

    /** Rechazar exige motivo (CHECK rechazo_con_motivo del SQL: no se guarda un rechazo mudo). */
    public void reject(User actor, String reason, Clock clock) {
        requireManager(actor);
        requirePending();
        this.rejectionReason = requireNotBlank(reason, "El motivo de rechazo no puede ser vacio");
        this.status = AccountRequestStatus.REJECTED;
        this.reviewedBy = actor.id();
        this.reviewedAt = clock.now();
        this.updatedAt = this.reviewedAt;
    }

    private void requirePending() {
        if (!status.isPending()) {
            throw new IllegalStateException("La solicitud ya fue decidida: " + status);
        }
    }

    private static void requireManager(User actor) {
        Objects.requireNonNull(actor, "Se requiere un actor para esta operacion");
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST deciden solicitudes de alta");
        }
    }

    /**
     * Dato opcional: ausente y vacio son lo mismo, y lo mismo se guarda como NULL. Evita que
     * convivan en la columna un null y un "" que significan identicamente "no lo dio" (D-61).
     */
    private static String normalizarOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "AccountRequest[" + id + ", " + email + ", " + status + "]";
    }
}
