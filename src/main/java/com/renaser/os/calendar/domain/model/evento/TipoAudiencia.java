package com.renaser.os.calendar.domain.model.evento;

/**
 * Tipo Postgres {@code tipo_audiencia}. Wire (app instalada, D-36):
 * ALL_MEMBERS/MIN_LEVEL/COURSE/ROLES/CELL (prisma {@code EventAudienceType}).
 *
 * <p>El campo que exige cada valor (CHECK {@code audiencia_coherente} del baseline) se
 * valida en {@link Evento}, no aqui: este enum es solo vocabulario.
 */
public enum TipoAudiencia {

    TODOS,
    NIVEL_MINIMO,
    CURSO,
    ROLES,
    CELULA
}
