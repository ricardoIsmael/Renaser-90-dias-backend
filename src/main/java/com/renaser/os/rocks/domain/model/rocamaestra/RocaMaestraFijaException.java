package com.renaser.os.rocks.domain.model.rocamaestra;

/**
 * Se pidio cambiar el objetivo, la meta, la unidad o el punto de partida de una Roca Maestra que
 * ya estaba definida (D-234).
 *
 * <p>La Roca Maestra nace del Mapa de Renacimiento del dia 7 y el dueno decidio el 2026-09-30 que
 * <b>no se cambia</b>: lo que se ajusta son los objetivos semanales y las acciones diarias. El
 * mensaje es apto para mostrarse a la persona tal cual.
 *
 * <p>Extiende {@link IllegalStateException} para que, si alguna vez sale por otro camino que no
 * sea {@code RocaMaestraController}, caiga igual en el 409 de {@code GlobalExceptionHandler}: el
 * pedido es valido, choca con el estado de la roca.
 */
public class RocaMaestraFijaException extends IllegalStateException {

    /** Codigo estable para que un cliente distinga este 409 de cualquier otro sin leer el texto. */
    public static final String CODIGO = "ROCA_MAESTRA_FIJA";

    private static final String MENSAJE = "Tu objetivo de 90 días quedó fijo en tu Mapa de Renacimiento y no se "
            + "cambia. Lo que sí puedes ajustar son tus objetivos semanales y tus acciones diarias.";

    public RocaMaestraFijaException() {
        super(MENSAJE);
    }
}
