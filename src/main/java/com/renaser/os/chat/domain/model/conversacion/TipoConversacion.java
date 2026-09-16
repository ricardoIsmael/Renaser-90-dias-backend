package com.renaser.os.chat.domain.model.conversacion;

/**
 * Espejo del tipo Postgres `tipo_conversacion` (V1__baseline_renaser.sql:82). En espanol
 * por vivir asi en la base y en el dominio (D-36); la traduccion a ingles para la app
 * publicada vive solo en `infrastructure/adapter/in/rest`.
 */
public enum TipoConversacion {
    CELULA,
    DIRECTA,
    GLOBAL,

    /**
     * El chat de soporte de UN aprendiz: el aprendiz mas todo el staff administrativo
     * (ADMIN/ALCHEMIST activos), creado solo cuando la persona entra al programa
     * (V53/V54, D-136). Viaja por HTTP como {@code SUPPORT}.
     *
     * <p>No es una DIRECTA con varios: una DIRECTA es de dos y la abre cualquiera de los dos.
     * Esta nace sola, tiene a la casa entera adentro y el aprendiz no se puede ir. Tampoco es
     * una CELULA: no cuelga de ningun grupo de `community` ni rota con el.
     */
    SOPORTE
}
