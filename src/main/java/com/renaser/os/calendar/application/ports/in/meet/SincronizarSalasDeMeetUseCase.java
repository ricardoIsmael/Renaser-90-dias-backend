package com.renaser.os.calendar.application.ports.in.meet;

import java.time.Instant;

/** Lo que hace el proceso en segundo plano (spec 040, FR-008, FR-009, FR-017): nada depende de que alguien guarde un evento. */
public interface SincronizarSalasDeMeetUseCase {

    /** Reintenta lo que quedó sin hacer con Google y cuyo turno ya llegó. @return cuántos eventos tocó */
    int reintentarLoPendiente(Instant ahora);

    /**
     * Cada hora (derivar, no incrementar: la regla 02): vuelve a calcular las salas de cada evento con Meet automático, así que las fechas que
     * entraron a la ventana reciben su sala y lo que cambió se corrige solo; avisa si la cuenta sigue revocada (un aviso por día) y purga lo viejo.
     */
    void mantenimiento(Instant ahora);
}
