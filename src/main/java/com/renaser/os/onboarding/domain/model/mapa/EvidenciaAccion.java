package com.renaser.os.onboarding.domain.model.mapa;

/** Con que se demuestra que la accion se hizo (§5.1). Opcional: no toda accion pide evidencia. */
public enum EvidenciaAccion {
    CHECK("check"),
    FOTO("foto"),
    VIDEO("video"),
    REGISTRO("registro"),
    DOCUMENTO("documento"),
    OTRO("otro");

    private final String clave;

    EvidenciaAccion(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    public static EvidenciaAccion desdeClave(String clave) {
        if (clave == null) {
            return null;
        }
        for (EvidenciaAccion evidencia : values()) {
            if (evidencia.clave.equals(clave)) {
                return evidencia;
            }
        }
        throw new IllegalArgumentException("Evidencia de accion desconocida: " + clave);
    }
}
