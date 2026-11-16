package com.renaser.os.shared.domain;

/** Spec 048 (FR-013): cómo calculó la app la hora real ({@code hechoEn}) de lo que la persona hizo sin red. */
public enum OrigenDeLaHora {
    /** La última hora del servidor más el reloj del sistema desde el encendido: cambiar la hora del teléfono no la mueve. */
    RELOJ_DEL_SISTEMA,
    /** El teléfono se reinició sin señal: su reloj corregido con el último desfase conocido. */
    TELEFONO_CORREGIDO;

    /** Un valor que no es de la lista se lee como el más desconfiable ({@link #TELEFONO_CORREGIDO}); vacío también. */
    public static OrigenDeLaHora leer(String valor) {
        if (valor == null || valor.isBlank()) {
            return TELEFONO_CORREGIDO;
        }
        try {
            return valueOf(valor.trim());
        } catch (IllegalArgumentException desconocido) {
            return TELEFONO_CORREGIDO;
        }
    }
}
