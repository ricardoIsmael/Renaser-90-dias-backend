package com.renaser.os.onboarding.domain.model.mapa;

/**
 * En que parte del dia cae una accion motora. Es informativo: NO restringe la hora.
 *
 * <p><b>Ojo con el nombre.</b> Es el mismo vocabulario que los "bloques del dia" que se sacaron de
 * la pantalla de Training el 2026-09-08 por pedido del cliente. Aca sigue existiendo porque el
 * modelo del Mapa lo trae ({@code AccionMotora.momento} en `tipos.ts`) y porque nunca acoto nada:
 * si tambien se quiere fuera del Mapa, es una decision de producto y se saca la columna.
 */
public enum MomentoAccion {
    MANANA("manana"),
    TARDE("tarde"),
    NOCHE("noche");

    private final String clave;

    MomentoAccion(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    public static MomentoAccion desdeClave(String clave) {
        if (clave == null) {
            return null;
        }
        for (MomentoAccion momento : values()) {
            if (momento.clave.equals(clave)) {
                return momento;
            }
        }
        throw new IllegalArgumentException("Momento de accion desconocido: " + clave);
    }
}
