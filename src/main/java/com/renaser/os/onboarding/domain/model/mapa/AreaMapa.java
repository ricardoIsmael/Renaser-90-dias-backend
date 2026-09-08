package com.renaser.os.onboarding.domain.model.mapa;

/**
 * Las tres areas del Mapa de Renacimiento (§5.1 del manual del Dia 7). Los literales son los
 * mismos que usa el cliente en `tipos.ts`, para que nadie tenga que traducir en el medio.
 */
public enum AreaMapa {
    SALUD("salud"),
    NEGOCIO_DINERO("negocio_dinero"),
    RELACIONES("relaciones");

    private final String clave;

    AreaMapa(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    public static AreaMapa desdeClave(String clave) {
        for (AreaMapa area : values()) {
            if (area.clave.equals(clave)) {
                return area;
            }
        }
        throw new IllegalArgumentException("Area de mapa desconocida: " + clave);
    }
}
