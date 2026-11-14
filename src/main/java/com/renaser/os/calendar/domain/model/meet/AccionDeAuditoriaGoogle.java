package com.renaser.os.calendar.domain.model.meet;

/** Lo que deja una fila de auditoría (spec 040, FR-016). */
public enum AccionDeAuditoriaGoogle {

    CREAR_SALA,
    ACTUALIZAR_SALA,
    BORRAR_SALA,
    AJUSTAR_ACCESO,
    REINTENTAR,
    CONECTAR,
    DESCONECTAR,
    CUENTA_REVOCADA,
    /** Pedir la grabación, la transcripción y las notas automáticas de la sala (spec 040, FR-036). */
    AJUSTAR_GRABACION,
    /** Hacer coanfitrión de la sala a quien dirige la sesión (spec 040, FR-038). */
    AJUSTAR_COANFITRIONES,
    /** Spec 047: la transcripción de una sesión quedó guardada (cifrada). */
    TRANSCRIPCION_GUARDADA,
    /** Spec 047: la sesión terminó sin transcripción que guardar (sin reunión, sin transcripción o sin permiso). */
    TRANSCRIPCION_SIN_DATOS,
    /** Spec 047: ADMIN o ALCHEMIST sacó una transcripción, con motivo. */
    TRANSCRIPCION_LEIDA,
    /** Spec 047: se borró una transcripción (retención, sala borrada). */
    TRANSCRIPCION_BORRADA
}
