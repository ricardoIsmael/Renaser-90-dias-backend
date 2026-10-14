package com.renaser.os.users.domain.model.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConflictoDeRolPersonalizadoException;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Un rol personalizado (spec 002, US7; FR-029 a FR-034; D-269): hereda el comportamiento de un rol base —la cuenta
 * guarda ese rol base en {@code usuarios.rol}, así que todos los guards lo ven igual— y marca un subconjunto de las
 * funcionalidades del catálogo que ese rol usa. Lo que no está marcado, a quien lo tenga, le queda «no disponible».
 *
 * <p>Qué decide este agregado y qué no:
 * <ul>
 *   <li>Sí: el rol base nunca es Soporte TI; el nombre (1 a 60) y la descripción (hasta 280); las claves con forma de
 *       clave; la versión; que con personas asignadas no cambie de base (FR-033).</li>
 *   <li>No: que cada clave exista en el catálogo y que el rol base la use. El catálogo vive en {@code plataforma}, que
 *       depende de {@code users} y no al revés: lo valida la consola antes de llamar (contrato de la fase 2, §4.5).</li>
 *   <li>No: que el nombre no se repita entre roles ni con un rol base, y el máximo de activos: dependen de otras
 *       filas ({@link ReglasDeRolesPersonalizados}).</li>
 * </ul>
 */
public final class RolPersonalizado {

    static final int LARGO_MAXIMO_DEL_NOMBRE = 60;
    static final int LARGO_MAXIMO_DE_LA_DESCRIPCION = 280;
    private static final String FORMA_DE_CLAVE = "[a-z_]+(\\.[a-z_]+)*";

    private final UUID id;
    private final String nombre;
    private final String descripcion;
    private final UserRole rolBase;
    private final boolean activo;
    private final Set<String> funcionalidades;
    private final long version;
    private final UserId creadoPor;
    private final Instant creadoEn;
    private final UserId actualizadoPor;
    private final Instant actualizadoEn;

    @SuppressWarnings("java:S107") // es la fila entera: rehidratar no admite menos
    private RolPersonalizado(UUID id, String nombre, String descripcion, UserRole rolBase, boolean activo,
                             Set<String> funcionalidades, long version, UserId creadoPor, Instant creadoEn,
                             UserId actualizadoPor, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.rolBase = rolBase;
        this.activo = activo;
        this.funcionalidades = funcionalidades;
        this.version = version;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoPor = actualizadoPor;
        this.actualizadoEn = actualizadoEn;
    }

    /** Un rol nuevo: nace activo y en la versión 1. */
    public static RolPersonalizado crear(UUID id, Datos datos, UserId actor, Instant ahora) {
        Objects.requireNonNull(actor, "actor es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        return new RolPersonalizado(id, nombreValido(datos.nombre()), descripcionValida(datos.descripcion()),
                rolBaseValido(datos.rolBase()), true, clavesValidas(datos.funcionalidades()), 1L, actor, ahora,
                actor, ahora);
    }

    @SuppressWarnings("java:S107")
    public static RolPersonalizado rehidratar(UUID id, String nombre, String descripcion, UserRole rolBase,
                                              boolean activo, Set<String> funcionalidades, long version,
                                              UserId creadoPor, Instant creadoEn, UserId actualizadoPor,
                                              Instant actualizadoEn) {
        return new RolPersonalizado(id, nombre, descripcion, rolBase, activo, ordenadas(funcionalidades), version,
                creadoPor, creadoEn, actualizadoPor, actualizadoEn);
    }

    /**
     * La versión siguiente con los datos nuevos. Cambiar el rol base con personas asignadas es 409 (FR-033): les
     * cambiaría lo que pueden hacer sin aviso; se crea otro rol y se reemplaza en bloque.
     *
     * @param datos    {@code rolBase} {@code null} = el mismo
     * @param personas cuántas cuentas lo tienen hoy
     */
    public RolPersonalizado editar(Datos datos, boolean activoNuevo, long personas, UserId actor, Instant ahora) {
        UserRole base = datos.rolBase() == null ? rolBase : rolBaseValido(datos.rolBase());
        if (base != rolBase && personas > 0) {
            throw ConflictoDeRolPersonalizadoException.conPersonas("«" + nombre + "» tiene " + personas
                    + " persona(s): no cambia de rol base. Crea otro rol y reemplázalo en bloque.");
        }
        return new RolPersonalizado(id, nombreValido(datos.nombre()), descripcionValida(datos.descripcion()), base,
                activoNuevo, clavesValidas(datos.funcionalidades()), version + 1, creadoPor, creadoEn,
                Objects.requireNonNull(actor, "actor es obligatorio"), Objects.requireNonNull(ahora));
    }

    /** Con personas asignadas no se borra (FR-033): se desactiva o se reemplaza en bloque. */
    public void exigirQueSePuedaBorrar(long personas) {
        if (personas > 0) {
            throw ConflictoDeRolPersonalizadoException.conPersonas("«" + nombre + "» tiene " + personas
                    + " persona(s): no se borra. Desactívalo o reemplázalo en bloque.");
        }
    }

    /** La clave estable con la que figura en la auditoría y en los roles asignables: {@code rp:<id>}. */
    public String clave() {
        return claveDe(id);
    }

    public static String claveDe(UUID id) {
        return "rp:" + id;
    }

    /** Si este nombre es el mismo que otro, sin mirar mayúsculas, tildes ni espacios de más. */
    public boolean seLlamaIgualQue(String otroNombre) {
        return NombreComparable.de(nombre).equals(NombreComparable.de(otroNombre));
    }

    public UUID id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }

    public UserRole rolBase() {
        return rolBase;
    }

    public boolean activo() {
        return activo;
    }

    public Set<String> funcionalidades() {
        return funcionalidades;
    }

    public long version() {
        return version;
    }

    public UserId creadoPor() {
        return creadoPor;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public UserId actualizadoPor() {
        return actualizadoPor;
    }

    public Instant actualizadoEn() {
        return actualizadoEn;
    }

    private static String nombreValido(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip().replaceAll("\\s+", " ");
        int largo = limpio.codePointCount(0, limpio.length());
        if (largo == 0 || largo > LARGO_MAXIMO_DEL_NOMBRE) {
            throw new IllegalArgumentException("El nombre del rol va de 1 a " + LARGO_MAXIMO_DEL_NOMBRE
                    + " caracteres");
        }
        return limpio;
    }

    private static String descripcionValida(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return null;
        }
        String limpia = descripcion.strip();
        if (limpia.codePointCount(0, limpia.length()) > LARGO_MAXIMO_DE_LA_DESCRIPCION) {
            throw new IllegalArgumentException("La descripción no puede pasar de " + LARGO_MAXIMO_DE_LA_DESCRIPCION
                    + " caracteres");
        }
        return limpia;
    }

    private static UserRole rolBaseValido(UserRole rolBase) {
        if (rolBase == null) {
            throw new IllegalArgumentException("Falta el rol base");
        }
        if (rolBase == UserRole.IT_SUPPORT) {
            throw new IllegalArgumentException("Un rol personalizado no hereda de Soporte TI");
        }
        return rolBase;
    }

    private static Set<String> clavesValidas(Set<String> claves) {
        if (claves == null) {
            throw new IllegalArgumentException("Faltan las funcionalidades del rol (puede ser una lista vacía)");
        }
        for (String clave : claves) {
            if (clave == null || !clave.matches(FORMA_DE_CLAVE)) {
                throw new IllegalArgumentException("«" + clave + "» no es una clave de funcionalidad");
            }
        }
        return ordenadas(claves);
    }

    private static Set<String> ordenadas(Set<String> claves) {
        return Collections.unmodifiableSet(new TreeSet<>(claves == null ? Set.of() : claves));
    }

    /**
     * Lo que se elige al crear o editar.
     *
     * @param descripcion     opcional
     * @param funcionalidades las claves marcadas; vacía es válido (todo lo que usa el rol base queda no disponible)
     */
    public record Datos(String nombre, String descripcion, UserRole rolBase, Set<String> funcionalidades) {
    }
}
