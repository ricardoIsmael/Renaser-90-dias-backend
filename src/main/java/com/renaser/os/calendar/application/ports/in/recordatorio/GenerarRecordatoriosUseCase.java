package com.renaser.os.calendar.application.ports.in.recordatorio;

import java.time.Instant;

public interface GenerarRecordatoriosUseCase {

    /** generar() del repo viejo: crea en la cola los avisos que faltan para los proximos
     * dias (ventana derivada de las reglas de cada evento). Devuelve cuantos se crearon. */
    int generar(Instant ahora);
}
