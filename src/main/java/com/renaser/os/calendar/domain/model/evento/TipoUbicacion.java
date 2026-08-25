package com.renaser.os.calendar.domain.model.evento;

/**
 * Tipo Postgres {@code tipo_ubicacion}. Wire (app instalada, D-36):
 * INTERNAL_CALL/WEBINAR/ZOOM/MEET/ADDRESS/LINK (prisma {@code EventLocationType}).
 */
public enum TipoUbicacion {

    LLAMADA_INTERNA,
    WEBINAR,
    ZOOM,
    MEET,
    DIRECCION,
    ENLACE
}
