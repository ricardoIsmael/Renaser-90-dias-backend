package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;

import java.time.Instant;

public interface SaveSalaMeetPort {

    SalaMeet guardar(SalaMeet sala);

    /** El evento va a cambiar o a borrarse: las salas ya creadas pasan a {@code PENDIENTE_DE_SINCRONIZAR} para que no se pierda el cambio si Google falla. */
    void marcarParaSincronizar(EventoId eventoId, Instant ahora);

    /** Reconectada la cuenta: lo que se había rendido de las fechas que no pasaron vuelve a intentarse (FR-009). */
    void reencolarLoPendiente(Instant ahora);

    /**
     * La cuenta quedó revocada: reintentar sería inútil (FR-009). Lo que esperaba a Google deja de reintentarse solo, con {@code motivo}; las salas
     * que ni siquiera se habían creado pasan a {@code FALLIDO}.
     */
    void detenerLoPendiente(String motivo, Instant ahora);

    /** Borra las lápidas viejas (retención de 12 meses). */
    int purgarLapidasAnterioresA(Instant limite);
}
