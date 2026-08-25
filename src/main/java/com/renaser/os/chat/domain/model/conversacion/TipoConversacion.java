package com.renaser.os.chat.domain.model.conversacion;

/**
 * Espejo del tipo Postgres `tipo_conversacion` (V1__baseline_renaser.sql:82). En espanol
 * por vivir asi en la base y en el dominio (D-36); la traduccion a ingles para la app
 * publicada vive solo en `infrastructure/adapter/in/rest`.
 */
public enum TipoConversacion {
    CELULA,
    DIRECTA,
    GLOBAL
}
