package com.renaser.os.users.domain.model.user;

import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


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
     * Cuando la persona cerro su cuenta para eliminarla ({@code usuarios.baja_solicitada_en}, D-243).
     * Mientras no sea {@code null} la cuenta esta cerrada: sin acceso (queda SUSPENDED) y fuera de lo
     * que ven los demas; al vencer la gracia el barrido la borra para siempre.
     *
     * <blockquote><b>Corregido 2026-10-02 (D-243).</b> Decia que la baja NO cortaba {@code hasAccess()}
     * para que la persona pudiera arrepentirse y cancelarla ella misma durante 14 dias (portada del
     * backend viejo). El dueño decidio lo contrario: la cuenta se cierra al instante y solo un Admin la
     * recupera, si la persona escribe a soporte dentro de los 30 dias.</blockquote>
     */
    private Instant bajaSolicitadaEn;
    /**
     * El rol personalizado asignado ({@code usuarios.rol_personalizado_id}, V115; spec 002, US7; D-272), o
     * {@code null}. <b>{@link #role} sigue siendo el rol base</b>: todos los guards, rankings y padrones lo ven igual, y
     * este dato solo lo lee la evaluación de interruptores y lo que muestra el nombre. Se conserva aunque el rol se
     * desactive (quienes lo tienen lo vuelven a tener si se reactiva); {@link #changeRole} lo borra.
     */
    private UUID rolPersonalizadoId;
    /**
     * El nombre de ese rol, <b>solo si está activo y hereda del rol de la cuenta</b>; {@code null} si no. Solo de
     * lectura: lo calcula la base al cargar la cuenta y nunca se guarda desde acá.
     */
    private String nombreDelRolPersonalizado;
    /**
     * Si es una cuenta de prueba de «ver como rol» ({@code usuarios.es_cuenta_de_prueba}, V117; spec 002, fase 2, US5;
     * D-273): una por rol, sin contraseña, que Soporte TI recorre en solo lectura. No figura en rankings, padrones,
     * semáforos, listas de Administración, chats ni avisos. Nace así y nunca deja de serlo (la columna no se actualiza).
     */
    private boolean esCuentaDePrueba;

    /**
     * Alta por autoregistro. Fuerza TRAINEE: el rol no es parametro a proposito,
     * es el blindaje de §5.3.3 llevado al compilador.
     */
    public static User registerTrainee(UserId id, Email email, String fullName) {
        return new User(requireId(id), requireEmail(email), UserRole.defaultForSelfRegistration(),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null, null, null, null, false);
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
                UserStatus.INACTIVE, requireName(fullName), null, null, null, null, null, null, null, false);
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
        requireNoEsSoporteTi(role);
        return new User(requireId(id), requireEmail(email), Objects.requireNonNull(role, "role es obligatorio"),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null, null, null, null, false);
    }

    /**
     * Cuenta de prueba de «ver como rol» (D-273): la crea el sistema, no una persona, así que no lleva actor (quien la
     * pide, Soporte TI, ya se comprobó en el caso de uso). Activa desde el alta, sin contraseña ni solicitud, con el rol
     * que se va a mirar; nunca Soporte TI. El correo es siempre del dominio reservado (lo exige también la base).
     */
    public static User cuentaDePrueba(UserId id, Email email, String fullName, UserRole role) {
        requireCorreoDePrueba(email);
        UserRole rol = requireNoEsSoporteTi(Objects.requireNonNull(role, "role es obligatorio"));
        return new User(requireId(id), email, rol,
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null, null, null, null, true);
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
        return rehydrate(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt,
                bajaSolicitadaEn, null, null);
    }

    /**
     * Con el rol personalizado (V115, D-272).
     *
     * @param nombreDelRolPersonalizado {@code null} si no tiene, o si el suyo está desactivado o no hereda de
     *                                  {@code role}
     */
    @SuppressWarnings("java:S107") // es la fila entera
    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarUrl, String bio, String department,
                                 Instant lastActiveAt, Instant bajaSolicitadaEn, UUID rolPersonalizadoId,
                                 String nombreDelRolPersonalizado) {
        return rehydrate(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt,
                bajaSolicitadaEn, rolPersonalizadoId, nombreDelRolPersonalizado, false);
    }

    /** Con la marca de cuenta de prueba (V117, D-273). */
    @SuppressWarnings("java:S107") // es la fila entera
    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarUrl, String bio, String department,
                                 Instant lastActiveAt, Instant bajaSolicitadaEn, UUID rolPersonalizadoId,
                                 String nombreDelRolPersonalizado, boolean esCuentaDePrueba) {
        return new User(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt,
                bajaSolicitadaEn, rolPersonalizadoId, nombreDelRolPersonalizado, esCuentaDePrueba);
    }

    /**
     * Deja la cuenta de prueba como tiene que estar para «ver como» ese rol (D-273): el nombre, el rol, activa, sin baja
     * y con el rol personalizado pedido (o sin ninguno). Es el camino interno del sistema que pidió B2 (D-272): no pasa
     * por {@link #asignarRolPersonalizado}, que exige un ADMIN o ALQUIMISTA como actor. Si alguien la tocó por fuera
     * (un script, una suspensión), vuelve a quedar bien.
     *
     * @param rolPersonalizado {@code null} para la cuenta de un rol base; si no, activo y que hereda de {@code role}
     * @return si cambió algo que haya que guardar
     */
    public boolean prepararComoCuentaDePrueba(String nombre, UserRole role, RolPersonalizado rolPersonalizado) {
        if (!esCuentaDePrueba) {
            throw new IllegalStateException("Solo una cuenta de prueba se prepara para «ver como»");
        }
        UserRole base = requireNoEsSoporteTi(Objects.requireNonNull(role, "role es obligatorio"));
        if (rolPersonalizado != null && (!rolPersonalizado.activo() || rolPersonalizado.rolBase() != base)) {
            throw new IllegalArgumentException("El rol personalizado de una cuenta de prueba está activo y hereda de "
                    + base.nombreVisible());
        }
        String nombreLimpio = requireName(nombre);
        UUID rolId = rolPersonalizado == null ? null : rolPersonalizado.id();
        String nombreRol = rolPersonalizado == null ? null : rolPersonalizado.nombre();
        boolean cambio = !nombreLimpio.equals(fullName) || role != this.role || status != UserStatus.ACTIVE
                || bajaSolicitadaEn != null || !Objects.equals(rolId, rolPersonalizadoId)
                || !Objects.equals(nombreRol, nombreDelRolPersonalizado);
        this.fullName = nombreLimpio;
        this.role = base;
        this.status = UserStatus.ACTIVE;
        this.bajaSolicitadaEn = null;
        this.rolPersonalizadoId = rolId;
        this.nombreDelRolPersonalizado = nombreRol;
        return cambio;
    }

    private static void requireCorreoDePrueba(Email email) {
        Objects.requireNonNull(email, "email es obligatorio");
        if (!CorreoDeCuentaDePrueba.loEs(email)) {
            throw new IllegalArgumentException("Una cuenta de prueba usa un correo ver-como+…@renaser.invalid");
        }
    }

    /** Una cuenta de prueba de «ver como» no se cambia desde Administración: la prepara el sistema (D-273). */
    private void requireNoEsCuentaDePrueba() {
        if (esCuentaDePrueba) {
            throw new NotAuthorizedException("Una cuenta de prueba de «ver como» no se cambia desde Administración");
        }
    }

    /**
     * Cambia el rol base y <b>quita el rol personalizado</b> que tuviera (contrato de la fase 2, §4.5: sin
     * {@code rolPersonalizadoId}, se borra el que tuviera). Así una cuenta nunca queda con un rol personalizado que
     * hereda de otro rol que el suyo, venga el cambio de donde venga.
     */
    public void changeRole(UserRole newRole, User actor) {
        requireRoleManager(actor);
        requireNoEsCuentaDePrueba();
        if (role == UserRole.IT_SUPPORT) {
            throw new NotAuthorizedException("Una cuenta Soporte TI no se cambia desde la app");
        }
        this.role = requireNoEsSoporteTi(Objects.requireNonNull(newRole, "El nuevo rol es obligatorio"));
        this.rolPersonalizadoId = null;
        this.nombreDelRolPersonalizado = null;
    }

    /**
     * Le asigna un rol personalizado (spec 002, FR-031; D-272): el rol de la cuenta pasa a ser el rol base de ese rol
     * y la evaluación de interruptores usa sus funcionalidades. Mismas reglas que asignar el rol base, más dos:
     * <ul>
     *   <li>solo un rol <b>activo</b> se asigna (uno desactivado no recibe a nadie nuevo, spec US7 escenario 4) → 400;</li>
     *   <li>un rol que hereda de ADMIN o de ALQUIMISTA lo asigna solo un ADMIN (FR-031) → 403.</li>
     * </ul>
     */
    public void asignarRolPersonalizado(RolPersonalizado rolPersonalizado, User actor) {
        requireRoleManager(actor);
        requireNoEsCuentaDePrueba();
        if (role == UserRole.IT_SUPPORT) {
            throw new NotAuthorizedException("Una cuenta Soporte TI no se cambia desde la app");
        }
        UserRole base = rolPersonalizado.rolBase();
        if ((base == UserRole.ADMIN || base == UserRole.ALCHEMIST) && actor.role() != UserRole.ADMIN) {
            throw new NotAuthorizedException("Solo un ADMIN asigna un rol que hereda de " + base.nombreVisible());
        }
        if (!rolPersonalizado.activo()) {
            throw new IllegalArgumentException("«" + rolPersonalizado.nombre() + "» está desactivado: no se asigna");
        }
        this.role = requireNoEsSoporteTi(base);
        this.rolPersonalizadoId = rolPersonalizado.id();
        this.nombreDelRolPersonalizado = rolPersonalizado.nombre();
    }

    /** El rol personalizado vigente: asignado, activo y que hereda del rol de la cuenta. */
    public Optional<RolPersonalizadoAsignado> rolPersonalizado() {
        if (rolPersonalizadoId == null || nombreDelRolPersonalizado == null) {
            return Optional.empty();
        }
        return Optional.of(new RolPersonalizadoAsignado(rolPersonalizadoId, nombreDelRolPersonalizado));
    }

    /**
     * Soporte TI se asigna SOLO desde la base ({@code scripts/soporte-ti/asignar.sql}, D-267, FR-022): ni invitar, ni
     * aprobar con ese rol (que pasa por {@link #changeRole}), ni cambiar el rol de alguien a Soporte TI. Es 400 y no
     * 403: el rol pedido no es uno que la app pueda dar, lo pida quien lo pida.
     */
    private static UserRole requireNoEsSoporteTi(UserRole rol) {
        if (rol == UserRole.IT_SUPPORT) {
            throw new IllegalArgumentException("El rol Soporte TI no se asigna desde la app");
        }
        return rol;
    }

    /** Si es la cuenta Soporte TI: Administración no la suspende, no la reactiva ni la elimina (D-267). */
    public boolean esSoporteTi() {
        return role == UserRole.IT_SUPPORT;
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
        if (bajaPendiente()) {
            // Reactivar dejaria una cuenta activa con el borrado programado: el barrido la borraria
            // igual. Se recupera con recuperarDeEliminacion(), que limpia las dos cosas (D-243).
            throw new IllegalStateException("Esta cuenta se cerro para eliminarla: recuperala en vez de reactivarla");
        }
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
     * La persona elimina su cuenta (D-243): se cierra en el acto y empieza la gracia. Una cuenta
     * activa pasa a SUSPENDED —no entra, sus sesiones y avisos caen como en cualquier suspension—; una
     * que ya no tenia acceso (pendiente de aprobacion o suspendida) conserva su estado. Idempotente:
     * repetirlo NO reinicia el plazo, que regalaria dias de gracia sin que nadie lo entienda.
     */
    public void cerrarParaEliminar(Clock clock) {
        if (bajaPendiente()) {
            return;
        }
        this.bajaSolicitadaEn = clock.now();
        if (status == UserStatus.ACTIVE) {
            this.status = UserStatus.SUSPENDED;
        }
    }

    /**
     * Un Admin recupera una cuenta cerrada dentro de la gracia (la persona escribio a soporte). Vuelve
     * a dar acceso si estaba suspendida; una pendiente de aprobacion sigue pendiente.
     */
    public void recuperarDeEliminacion() {
        if (!bajaPendiente()) {
            throw new IllegalStateException("Esta cuenta no esta cerrada para eliminar");
        }
        this.bajaSolicitadaEn = null;
        if (status == UserStatus.SUSPENDED) {
            this.status = UserStatus.ACTIVE;
        }
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
