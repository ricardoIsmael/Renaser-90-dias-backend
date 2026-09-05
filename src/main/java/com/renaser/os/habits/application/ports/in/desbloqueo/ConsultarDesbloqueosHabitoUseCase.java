package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Hueco #12 — lectura del plan de desbloqueo escalonado del aprendiz (`habitStaggering.ts`).
 * SOLO LECTURA en esta pasada: el algoritmo de relleno/reacomodo por lotes (~1470 lineas del
 * repo viejo) sigue fuera de alcance (D-H2) — esto expone lo que ya este guardado en
 * `desbloqueos_habito`, sin reorganizar nada.
 */
public interface ConsultarDesbloqueosHabitoUseCase {

    PlanDesbloqueo consultar(UserId actorId);

    /**
     * {@code enabled}: aproximacion simplificada de la semantica vieja ("esta cuenta tiene
     * escalonamiento aplicado") — {@code true} si hay al menos un desbloqueo guardado. El
     * repo viejo lo resolvia por un campo propio del perfil ({@code staggeredHabitsAt}) que
     * este backend no tiene todavia.
     */
    record PlanDesbloqueo(boolean enabled, List<ItemDesbloqueo> items) {
    }

    /**
     * @param pausado      hay una pausa registrada para este aprendiz (V23).
     * @param pausadoHasta ultimo dia INCLUSIVE de esa pausa, o {@code null} si es indefinida (V31).
     */
    record ItemDesbloqueo(HabitoId habitoId, int diaDesbloqueo, Instant elegidoEn, boolean pausado,
                           LocalDate pausadoHasta) {
    }
}
