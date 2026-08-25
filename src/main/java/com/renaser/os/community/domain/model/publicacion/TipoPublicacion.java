package com.renaser.os.community.domain.model.publicacion;

/**
 * Espejo del tipo Postgres `tipo_publicacion` (V1__baseline_renaser.sql:73). En espanol
 * por vivir asi en la base y en el dominio (D-36); la traduccion a ingles para la app
 * publicada vive solo en `infrastructure/adapter/in/rest`.
 *
 * <p>{@code HITO_AUTOMATICO} y {@code GUERRERO_CAIDO} nunca se crean via POST — el codigo
 * viejo los documenta como generados por "system triggers" (app/api/v1/wall/route.ts:9-11)
 * pero esos triggers no existen en ningun lado del repo viejo (busqueda completa, sin
 * resultados). Este modulo solo produce MANUAL; ver docs/MODULO_COMMUNITY.md sec. 6.
 */
public enum TipoPublicacion {
    MANUAL,
    HITO_AUTOMATICO,
    GUERRERO_CAIDO
}
