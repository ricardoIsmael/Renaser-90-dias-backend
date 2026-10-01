package com.renaser.os.leadership.domain.model.observacion;

/**
 * Qué le dice el líder al mentor (SDD 002, propuesta PL-04): reconocer lo que hace bien, sugerirle un
 * cambio, o alertarlo de algo que va mal. Cerrado: la base lo repite con un CHECK (V88).
 */
public enum TipoObservacion {
    RECONOCIMIENTO,
    SUGERENCIA,
    ALERTA;

    public static TipoObservacion de(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de observacion es obligatorio: RECONOCIMIENTO, SUGERENCIA o ALERTA");
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de observacion invalido: " + valor
                    + ". Usa RECONOCIMIENTO, SUGERENCIA o ALERTA");
        }
    }
}
