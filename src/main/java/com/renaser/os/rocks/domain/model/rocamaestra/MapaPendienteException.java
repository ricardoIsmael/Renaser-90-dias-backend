package com.renaser.os.rocks.domain.model.rocamaestra;

/**
 * Se pidio definir o cambiar el objetivo de 90 dias desde fuera del Mapa de Renacimiento, y el Mapa de la
 * persona todavia no esta activo (D-284, decision del dueno del 2026-10-27).
 *
 * <p>El objetivo de 90 dias nace del Mapa: hasta que el Mapa este activo, lo unico que lo escribe es la
 * propia activacion del Mapa. El Mapa sigue siendo opcional para el resto de la app; lo que se bloquea es
 * este objetivo. El mensaje es apto para mostrarse a la persona tal cual.
 *
 * <p>Extiende {@link IllegalStateException} por la misma razon que {@link RocaMaestraFijaException}: si
 * alguna vez sale por otro camino que no sea {@code RocaMaestraController}, cae igual en el 409 de
 * {@code GlobalExceptionHandler}.
 */
public class MapaPendienteException extends IllegalStateException {

    /** Codigo estable para que un cliente distinga este 409 de cualquier otro sin leer el texto. */
    public static final String CODIGO = "MAPA_PENDIENTE";

    private static final String MENSAJE = "Primero completa tu Mapa de Renacimiento. Tu objetivo de 90 días "
            + "se define ahí y después queda fijo.";

    public MapaPendienteException() {
        super(MENSAJE);
    }
}
