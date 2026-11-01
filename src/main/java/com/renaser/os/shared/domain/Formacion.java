package com.renaser.os.shared.domain;

import java.util.Arrays;

/**
 * Las tres formaciones de Renaser (spec 022, D-295): Escuela, Diplomado y Certificación. Son fijas —no se crean ni se borran desde la
 * app—; lo que se edita (nombre, descripción, duraciones) vive en la tabla {@code formaciones}, cuya clave es {@link #codigo()}.
 *
 * <p>Es el límite de aislamiento: ninguna lectura ni acción cruza formaciones. Vive en el núcleo compartido porque todos los
 * módulos filtran por ella (specs 023 y 024), y {@code users.api.AlcanceDeFormacionFinder} es el único lugar que dice a cuáles
 * tiene acceso quien actúa.
 *
 * <p>Una cuarta formación en el futuro es un valor más de este enum y una fila más del catálogo: no obliga a tocar las tres existentes.
 */
public enum Formacion {

    ESCUELA,
    DIPLOMADO,
    CERTIFICACION;

    /** Todo lo que existía antes de las formaciones es de la Escuela (FR-005); también el valor por defecto de las columnas. */
    public static final Formacion POR_DEFECTO = ESCUELA;

    /** La clave en la base y en la API (`ESCUELA`, `DIPLOMADO`, `CERTIFICACION`). */
    public String codigo() {
        return name();
    }

    /**
     * Para el detalle de UNA formación (`GET /admin/formaciones/{codigo}`, contrato 022): una formación que no existe es un recurso que no existe (404),
     * no una entrada mal formada. Los demás códigos (consulta, cuerpo y las escrituras sobre la ruta) siguen usando {@link #desde(String)} (400).
     *
     * @throws java.util.NoSuchElementException si el código no es de una formación (404)
     */
    public static Formacion deLaRuta(String codigo) {
        try {
            return desde(codigo);
        } catch (IllegalArgumentException noExiste) {
            throw new java.util.NoSuchElementException(noExiste.getMessage());
        }
    }

    /**
     * @throws IllegalArgumentException si el código no es de una formación (400)
     */
    public static Formacion desde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("La formación es obligatoria.");
        }
        String limpio = codigo.trim();
        return Arrays.stream(values())
                .filter(formacion -> formacion.name().equalsIgnoreCase(limpio))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe la formación «" + limpio + "»."));
    }
}
