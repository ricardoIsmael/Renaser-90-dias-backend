package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * En que dia de programa se desbloquea un habito para este participante (tabla
 * `desbloqueos_habito`) — resultado del escalonamiento por lotes del repo viejo
 * (`habitStaggering.ts`/`staggerService.ts`, ~1470 lineas, D-H2: el ALGORITMO de relleno
 * no se porto en esta pasada). Este agregado es SOLO LECTURA por ahora: expone lo que ya
 * este guardado en la tabla, sin reacomodar lotes.
 *
 * <p>{@code elegidoEn == null} significa que lo puso el relleno automatico, no el aprendiz
 * (semantica actual de la columna, documentada en el baseline).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class DesbloqueoHabito {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private final int diaDesbloqueo;
    private final Instant elegidoEn;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    /** Solo para el adaptador de persistencia — sin caso de uso de escritura en esta pasada (D-H2). */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn) {
        return new DesbloqueoHabito(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn);
    }

    @Override
    public String toString() {
        return "DesbloqueoHabito[" + participanteId + ", " + habitoId + ", dia " + diaDesbloqueo + "]";
    }
}
