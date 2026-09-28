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
     * URL PERMANENTE y sin firmar de la foto de perfil (el objeto del avatar es de lectura
     * publica, D-55). Es la unica excepcion deliberada a la regla P-03 del esquema — el resto
     * de los binarios guarda ruta y se firma al leer — y se sostiene solo mientras el objeto
     * sea publico: una URL PREFIRMADA guardada aca vence y no se vuelve a firmar jamas, que fue
     * exactamente el defecto E-57. {@link #changeAvatar} rechaza las prefirmadas por eso.
     */
    private String avatarUrl;
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
                                 String fullName, String avatarUrl, String bio, String department,
                                 Instant lastActiveAt) {
        return rehydrate(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt, null);
    }

    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarUrl, String bio, String department,
                                 Instant lastActiveAt, Instant bajaSolicitadaEn) {
        return new User(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt,
                bajaSolicitadaEn);
    }

    public void changeRole(UserRole newRole, User actor) {
        requireRoleManager(actor);
        this.role = Objects.requireNonNull(newRole, "El nuevo rol es obligatorio");
    }

    /**
     * Quita el acceso a una cuenta YA APROBADA (repetirlo no cambia nada). Una que nunca se aprobó no se
     * suspende: suspenderla y reactivarla era la puerta de atrás de {@link #reactivate()} en dos pasos
     * (E-367). A alguien pendiente se le rechaza la solicitud.
     */
    public void suspend() {
        requireAprobada("para que no entre, rechaza su solicitud de alta");
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * Devuelve el acceso a una cuenta suspendida (repetirlo no cambia nada). <b>Nunca activa una cuenta
     * que no se aprobó</b> (E-367, ADM-20 del e2e del 2026-09-27): antes ponía ACTIVE sin mirar de dónde
     * venía, y dejaba entrar a alguien pendiente sin programa. El alta pasa solo por {@link #aprobar()}
     * (que además inscribe el programa) o por la invitación ({@code .claude/rules/04}).
     */
    public void reactivate() {
        requireAprobada("se activa aprobando su solicitud de alta");
        this.status = UserStatus.ACTIVE;
    }

    /** Si la cuenta nunca se aprobó: la creó el alta y espera que alguien decida su solicitud. */
    public boolean pendienteDeAprobacion() {
        return status == UserStatus.INACTIVE;
    }

    private void requireAprobada(String queHacerEnCambio) {
        if (pendienteDeAprobacion()) {
            throw new IllegalStateException("Esta cuenta todavía no fue aprobada: " + queHacerEnCambio);
        }
    }

    public void rename(String newFullName) {
        this.fullName = requireName(newFullName);
    }

    /**
     * Guarda la URL PERMANENTE del avatar. {@code null} o vacio quita el avatar.
     *
     * <p>Rechaza las URLs PREFIRMADAS, que es la unica forma conocida de romper esto: una
     * prefirmada trae su propio vencimiento y, guardada, deja de servir el dia que caduca sin
     * que nada la vuelva a firmar. Paso de verdad — el avatar se firmaba por 7 dias y se
     * persistia (E-57) — y no se notaba porque el defecto tarda una semana en aparecer. El
     * chequeo es barato y es la razon por la que no puede repetirse en silencio.
     */
    public void changeAvatar(String newAvatarUrl) {
        this.avatarUrl = requireUrlNoPrefirmada(newAvatarUrl);
    }

    /** Marcas de SigV4 en la query string. Nombres del estandar de AWS, no de nuestro codigo. */
    private static final String[] MARCAS_DE_URL_PREFIRMADA = {"x-amz-signature", "x-amz-credential",
            "x-amz-expires"};

    private static String requireUrlNoPrefirmada(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String enMinusculas = url.toLowerCase(java.util.Locale.ROOT);
        for (String marca : MARCAS_DE_URL_PREFIRMADA) {
            if (enMinusculas.contains(marca)) {
                throw new IllegalArgumentException(
                        "El avatar guarda una URL permanente; una URL prefirmada vence y dejaria la foto rota");
            }
        }
        return url.trim();
    }

    /**
     * Topes de la biografía y del departamento (E-369, SEG-16 del e2e del 2026-09-27: {@code PATCH
     * /users/me} guardaba 1.048.576 caracteres en cada uno). La app no pone ningún límite en esos dos
     * campos, así que el servidor es el único que puede. <b>Decisión técnica, a confirmar con el
     * dueño</b>: la biografía es un texto de varias líneas que se lee en la ficha (1000 es el mismo tope
     * que un mensaje de bienvenida, {@code TextoDeBienvenida}); el departamento es una línea, como el
     * nombre, y lleva su mismo tope.
     */
    static final int LARGO_MAXIMO_DE_LA_BIOGRAFIA = 1000;
    static final int LARGO_MAXIMO_DEL_DEPARTAMENTO = 120;

    public void updateBio(String newBio) {
        this.bio = requireLargoMaximo(newBio, LARGO_MAXIMO_DE_LA_BIOGRAFIA, "La biografía");
    }

    public void updateDepartment(String newDepartment) {
        this.department = requireLargoMaximo(newDepartment, LARGO_MAXIMO_DEL_DEPARTAMENTO, "El departamento");
    }

    /**
     * Cuenta caracteres como los cuenta Postgres ({@code char_length}): un emoji es uno, no dos. El
     * texto se guarda tal cual; {@code null} sigue siendo «sin cambios» para quien llama. Como en el
     * nombre, {@code rehydrate} no pasa por acá: lo que ya está guardado se carga como está.
     */
    private static String requireLargoMaximo(String texto, int largoMaximo, String campo) {
        if (texto != null && texto.codePointCount(0, texto.length()) > largoMaximo) {
            throw new IllegalArgumentException(campo + " no puede pasar de " + largoMaximo + " caracteres");
        }
        return texto;
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

    /** Generoso para cualquier nombre real, incluidos los compuestos con varios apellidos. */
    private static final int LARGO_MAXIMO_DEL_NOMBRE = 120;

    /**
     * El nombre es <b>texto que la persona se escribe a si misma</b> —{@code PATCH /users/me} lo
     * acepta sin mas— y no se queda en su perfil: los barridos que corren sin actor lo renderizan
     * dentro de mensajes del sistema para OTRA gente. {@code AvisosService} lo mete en la
     * notificacion y en el push que recibe su mentor, y arranca la frase. Sin cota ni saneo, un
     * aprendiz podia redactar parrafos enteros con saltos de linea que en la bandeja del mentor
     * se leen como texto de Renaser y no como el nombre de un companero.
     *
     * <p>Por eso el saneo va aca, en el borde del dominio, y no en el controller: asi vale para
     * los tres caminos de alta y para el cambio de nombre, y no solo para el endpoint que alguien
     * se acuerde de anotar. La base guarda {@code text} sin cota, asi que no habia nada detras.
     *
     * <p>{@code rehydrate} no pasa por aca a proposito: los nombres que ya estan guardados se
     * cargan como estan, y este limite solo gobierna lo que se escribe de ahora en adelante.
     */
    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede ser vacio");
        }
        String enUnaSolaLinea = fullName.replaceAll("\\s+", " ").trim();
        if (enUnaSolaLinea.length() > LARGO_MAXIMO_DEL_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre no puede pasar de " + LARGO_MAXIMO_DEL_NOMBRE + " caracteres");
        }
        return enUnaSolaLinea;
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + role + ", " + status + "]";
    }
}
