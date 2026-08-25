package com.renaser.os.academy.application.ports.out.progreso;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.domain.UserId;

public interface SaveProgresoLeccionPort {

    /** Idempotente: completar dos veces la misma leccion no duplica fila ni cambia `completadaEn` (upsert-ignore). */
    ProgresoLeccion marcarCompletada(ProgresoLeccion progreso);

    /** Idempotente: borrar una fila que no existe no falla (AC-16). */
    void desmarcarCompletada(UserId usuarioId, LeccionId leccionId);
}
