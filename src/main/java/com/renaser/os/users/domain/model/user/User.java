package com.renaser.os.users.domain.model.user;

import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;


@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class User {

    private final UserId id;
    private final Email email;
    private UserRole role;
    private UserStatus status;
    private String fullName;
    /**
     * RUTA dentro del bucket privado ({@code avatares/{id}}), JAMAS una URL — misma regla
     * P-03 que el resto del esquema ({@code ruta_storage}, {@code ruta_firma},
     * {@code adjunto_ruta}...). La URL de lectura se firma AL LEER, en cada respuesta, y
     * no se persiste nunca: una URL firmada guardada vence y no vuelve a firmarse jamas
     * (E-57). {@link #changeAvatar} rechaza cualquier otro valor.
     */
    private String avatarRuta;
    /** Solo tiene sentido si role == ALCHEMIST. Sin tabla propia: decisión 2026-08-24, ver D-25. */
    private String bio;
    /** Solo tiene sentido si role == ADMIN. Sin tabla propia: decisión 2026-08-24, ver D-25. */
    private String department;
    private Instant lastActiveAt;
    /**
     * Baja de cuenta autogestionada (usuarios.baja_solicitada_en) - soft-delete diferido,
     * NO UserStatus: a proposito NO corta hasAccess() (backend viejo,
     * features/account-deletion/plazo.ts#conservaAcceso - sin acceso durante la gracia no
     * habria forma de arrepentirse y cancelar). Un cron purga (hard delete) al vencer el
     * plazo de gracia - ver AccountDeletionService y EstadoBajaCuenta.
     */
    private Instant bajaSolicitadaEn;

    /**
     * Alta por autoregistro. Fuerza TRAINEE: el rol no es parametro a proposito,
     * es el blindaje de §5.3.3 llevado al compilador.
     */
    public static User registerTrainee(UserId id, Email email, String fullName) {
        return new User(requireId(id), requireEmail(email), UserRole.defaultForSelfRegistration(),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null, null);
    }

    /**
     * Autoregistro: la persona lleno el formulario y verifico su correo, pero todavia no la
     * aprobo nadie (2026-08-27).
     *
     * <p>La fila se crea ACA y no al aprobar porque el alta ahora captura la contrasena en el
     * formulario, y {@code hash_contrasena} vive en {@code usuarios} — es la unica tabla donde
     * puede guardarse sin duplicar la credencial. Nace {@link UserStatus#INACTIVE}, que no da
     * acceso: el login la rechaza por construccion hasta que {@link #aprobar()} la active.
     *
     * <p>El rol lo fuerza {@link UserRole#defaultForSelfRegistration()}, igual que
     * {@link #registerTrainee}: nunca llega desde el cliente (§5.3.3).
     */
    public static User registrarPendienteAprobacion(UserId id, Email email, String fullName) {
        return new User(requireId(id), requireEmail(email), UserRole.defaultForSelfRegistration(),
                UserStatus.INACTIVE, requireName(fullName), null, null, null, null, null);
    }

    /**
     * Un admin aprobo la solicitud: la cuenta pasa a dar acceso. Solo tiene sentido sobre una
     * cuenta recien registrada — aprobar algo que no esta {@link UserStatus#INACTIVE} seria
     * reactivar a alguien suspendido por la puerta de atras, que es otra operacion
     * ({@link #reactivate()}) y con otra autorizacion.
     */
    public void aprobar() {
        if (status != UserStatus.INACTIVE) {
            throw new IllegalStateException("Solo se puede aprobar una cuenta pendiente de aprobacion");
        }
        this.status = UserStatus.ACTIVE;
    }

    /** Alta por invitacion de un admin, con rol explicito (§5.3.3, InviteAndCreateUser). */
    public static User invite(UserId id, Email email, String fullName, UserRole role, User actor) {
        requireRoleManager(actor);
        return new User(requireId(id), requireEmail(email), Objects.requireNonNull(role, "role es obligatorio"),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null, null);
    }

    /** Firma historica (9 campos, sin bajaSolicitadaEn): se conserva para no obligar a
     * todos los llamadores existentes (produccion y ~15 archivos de test) a agregar un
     * campo que la mayoria no necesita rehidratar. */
    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarRuta, String bio, String department,
                                 Instant lastActiveAt) {
        return rehydrate(id, email, role, status, fullName, avatarRuta, bio, department, lastActiveAt, null);
    }

    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarRuta, String bio, String department,
                                 Instant lastActiveAt, Instant bajaSolicitadaEn) {
        return new User(id, email, role, status, fullName, avatarRuta, bio, department, lastActiveAt,
                bajaSolicitadaEn);
    }

    public void changeRole(UserRole newRole, User actor) {
        requireRoleManager(actor);
        this.role = Objects.requireNonNull(newRole, "El nuevo rol es obligatorio");
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }

    public void rename(String newFullName) {
        this.fullName = requireName(newFullName);
    }

    /**
     * Guarda la RUTA del avatar, no su URL. Rechazar cualquier otra cosa es lo que impide
     * que vuelva a colarse una URL firmada en la columna (E-57): sin este chequeo, un
     * {@code PATCH} con {@code "https://..."} — o con la ruta de OTRO usuario — quedaria
     * persistido y despues se firmaria como lectura valida del bucket privado.
     *
     * @param nuevaRutaAvatar {@link #rutaAvatarDe} de este mismo usuario; {@code null} o
     *                        vacio quita el avatar.
     */
    public void changeAvatar(String nuevaRutaAvatar) {
        this.avatarRuta = requireRutaAvatarPropia(nuevaRutaAvatar);
    }

    /**
     * Ruta determinista del avatar dentro del bucket privado. Es determinista a proposito:
     * permite recomputarla desde el id (asi la migracion V13 pudo reparar las filas que
     * tenian una URL firmada congelada) y deja una sola ruta posible por usuario.
     */
    public static String rutaAvatarDe(UserId id) {
        return "avatares/" + requireId(id);
    }

    private String requireRutaAvatarPropia(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        String propia = rutaAvatarDe(id);
        if (!propia.equals(ruta.trim())) {
            throw new IllegalArgumentException(
                    "El avatar se guarda como la ruta de storage propia del usuario, nunca una URL ni una ruta ajena");
        }
        return propia;
    }

    public void updateBio(String newBio) {
        this.bio = newBio;
    }

    public void updateDepartment(String newDepartment) {
        this.department = newDepartment;
    }

    public void touchLastActive(Clock clock) {
        this.lastActiveAt = clock.now();
    }

    /**
     * Idempotente a proposito: repetir la solicitud NO reinicia el contador (backend viejo,
     * service.ts#solicitarBaja) - si reiniciara, pulsar dos veces regalaria dias de gracia
     * de mas sin que el usuario lo entienda.
     */
    public void solicitarBaja(Clock clock) {
        if (this.bajaSolicitadaEn == null) {
            this.bajaSolicitadaEn = clock.now();
        }
    }

    /** Deshace la solicitud. No deja rastro: vuelve a null, igual que el backend viejo. */
    public void cancelarBaja() {
        this.bajaSolicitadaEn = null;
    }

    public boolean bajaPendiente() {
        return bajaSolicitadaEn != null;
    }

    public boolean canManageRoles() {
        return role.canManageRoles();
    }

    public boolean hasAccess() {
        return status.allowsAccess();
    }

    private static void requireRoleManager(User actor) {
        Objects.requireNonNull(actor, "Se requiere un actor para esta operacion");
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST cambian roles");
        }
    }

    private static UserId requireId(UserId id) {
        return Objects.requireNonNull(id, "id es obligatorio");
    }

    private static Email requireEmail(Email email) {
        return Objects.requireNonNull(email, "email es obligatorio");
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede ser vacio");
        }
        return fullName.trim();
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + role + ", " + status + "]";
    }
}
