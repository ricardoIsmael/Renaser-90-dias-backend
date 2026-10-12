package com.renaser.os.habits.application.ports.out.audiencia;

import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeRol;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/** Los roles que tuvo cada persona en el tiempo (V96, {@code historial_rol_participante}). */
public interface HistorialDeRolPort {

    HistorialDeRol de(UserId persona);

    /**
     * Anota un cambio de rol. Si es el primero de esa persona, anota antes el rol anterior "desde siempre". Idempotente:
     * el outbox puede reentregar el mismo evento.
     */
    void anotarCambio(UserId persona, CodigoDeRol anterior, CodigoDeRol nuevo, Instant cuando);
}
