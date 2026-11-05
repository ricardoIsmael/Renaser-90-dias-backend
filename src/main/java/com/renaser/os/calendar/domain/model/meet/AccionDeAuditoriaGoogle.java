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
    AJUSTAR_GRABACION
}
