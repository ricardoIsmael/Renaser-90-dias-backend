package com.renaser.os.habits.application.ports.out.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface SaveDesbloqueoHabitoPort {

    /**
     * Inserta el desbloqueo si todavia no existe fila para (participanteId, habitoId) — la PK
     * compuesta de {@code desbloqueos_habito} la hace naturalmente idempotente: INSERT ...
     * ON CONFLICT DO NOTHING, mismo patron que {@code SavePuntajePort.crearFilaInicialSiFalta}
     * (C-12/E-75, docs/BITACORA_ERRORES.md): elegir dos veces el mismo habito nunca falla ni
     * duplica, y si dos elecciones concurrentes chocan, Postgres serializa el segundo INSERT
     * contra la restriccion UNIQUE de la PK en vez de que las dos transacciones violen la PK.
     *
     * <p>A diferencia de {@code puntajes_participante}, esta fila no se vuelve a leer para
     * ESCRIBIR despues de creada dentro de este caso de uso (no hay acumulacion numerica que
     * proteger con un bloqueo pesimista posterior) — el llamador solo necesita releer con
     * {@link LoadDesbloqueoHabitoPort} para devolver el estado canonico, sea cual sea el
     * intento que gano la carrera de creacion.
     */
    void elegirSiFalta(UserId participanteId, HabitoId habitoId, int diaDesbloqueo, Instant elegidoEn, Instant ahora);

    /** Persiste un desbloqueo ya existente — hoy solo cambia `pausado_en` (V23, D-87). */
    DesbloqueoHabito save(DesbloqueoHabito desbloqueo);

    /** Saca el habito del plan. Idempotente: borrar lo que no esta no falla. */
    void borrar(UserId participanteId, HabitoId habitoId);
}
