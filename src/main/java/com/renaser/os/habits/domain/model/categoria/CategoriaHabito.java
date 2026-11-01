package com.renaser.os.habits.domain.model.categoria;

import com.renaser.os.shared.domain.Formacion;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Una categoria de habito de UNA formacion (spec 023; tabla {@code categorias_habito}). En Training es una dimension: Cuerpo, Mente…
 * Cada formacion tiene las suyas, con su nombre, su orden y su icono. La clave es la llave primaria y no cambia nunca; las cuatro de la Escuela
 * conservan las de siempre ({@code CUERPO}, {@code MENTE}, {@code CONSCIENCIA}, {@code ESPIRITU}) y una nueva toma
 * {@code <FORMACION>_<NOMBRE>}.
 *
 * <p>Lo que depende de otras filas —que el nombre no se repita en la formacion, que no se desactive con habitos activos, que no se borre con
 * habitos— lo decide el servicio (regla 04 de las migraciones: una invariante que cruza filas no es del agregado).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "clave")
public final class CategoriaHabito {

    public static final int NOMBRE_MINIMO = 2;
    public static final int NOMBRE_MAXIMO = 40;
    private static final int CLAVE_MAXIMA = 60;
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[A-Z][A-Z0-9_]{2,59}");
    private static final Pattern NO_CLAVE = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private final String clave;
    private final Formacion formacion;
    private String nombre;
    private int orden;
    private String icono;
    private boolean activa;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static CategoriaHabito crear(String clave, Formacion formacion, String nombre, int orden, String icono,
                                         Instant ahora) {
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        return new CategoriaHabito(requireClave(clave), formacion, requireNombre(nombre), requireOrden(orden),
                IconoDeCategoria.validar(icono), true, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static CategoriaHabito rehydrate(String clave, Formacion formacion, String nombre, int orden, String icono,
                                             boolean activa, Instant creadoEn, Instant actualizadoEn) {
        return new CategoriaHabito(clave, formacion, nombre, orden, icono, activa, creadoEn, actualizadoEn);
    }

    /**
     * La clave que le toca a una categoria nueva: {@code <FORMACION>_<NOMBRE>} en mayusculas, sin tildes ni signos
     * ({@code DIPLOMADO_ESTUDIO_PROFUNDO}). Si ya existe, el servicio prueba con un sufijo numerico.
     */
    public static String claveNueva(Formacion formacion, String nombre) {
        String sinTildes = MARCAS.matcher(Normalizer.normalize(requireNombre(nombre), Normalizer.Form.NFD)).replaceAll("");
        String palabra = NO_CLAVE.matcher(sinTildes.toUpperCase(Locale.ROOT)).replaceAll("_");
        palabra = palabra.replaceAll("^_+|_+$", "");
        String clave = formacion.codigo() + "_" + (palabra.isEmpty() ? "CATEGORIA" : palabra);
        return clave.length() > CLAVE_MAXIMA ? clave.substring(0, CLAVE_MAXIMA).replaceAll("_+$", "") : clave;
    }

    public void renombrar(String nuevoNombre, Instant ahora) {
        this.nombre = requireNombre(nuevoNombre);
        this.actualizadoEn = ahora;
    }

    public void cambiarIcono(String nuevoIcono, Instant ahora) {
        this.icono = IconoDeCategoria.validar(nuevoIcono);
        this.actualizadoEn = ahora;
    }

    public void mover(int nuevoOrden, Instant ahora) {
        this.orden = requireOrden(nuevoOrden);
        this.actualizadoEn = ahora;
    }

    public void activar(Instant ahora) {
        this.activa = true;
        this.actualizadoEn = ahora;
    }

    public void desactivar(Instant ahora) {
        this.activa = false;
        this.actualizadoEn = ahora;
    }

    /** El nombre sin tildes ni mayusculas: con lo que se decide si dos nombres de la misma formacion son el mismo (FR-003). */
    public static String nombreNormalizado(String nombre) {
        return MARCAS.matcher(Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String requireClave(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            throw new IllegalArgumentException("La clave de la categoría no es válida: " + clave);
        }
        return clave;
    }

    private static String requireNombre(String nombre) {
        if (nombre == null || nombre.trim().length() < NOMBRE_MINIMO || nombre.trim().length() > NOMBRE_MAXIMO) {
            throw new IllegalArgumentException("El nombre de la categoría va de " + NOMBRE_MINIMO + " a " + NOMBRE_MAXIMO
                    + " caracteres.");
        }
        return nombre.trim();
    }

    private static int requireOrden(int orden) {
        if (orden < 0) {
            throw new IllegalArgumentException("El orden de la categoría no puede ser negativo.");
        }
        return orden;
    }
}
