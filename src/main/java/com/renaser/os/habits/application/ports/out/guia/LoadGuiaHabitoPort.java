package com.renaser.os.habits.application.ports.out.guia;

import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadGuiaHabitoPort {

    /** UNA sola consulta para N habitos — proyeccion de lectura (hueco #10), nunca N+1. */
    List<GuiaHabito> porHabitos(Collection<HabitoId> habitoIds);

    /** Panel admin, hueco #11: TODAS las guias de un habito (no solo la vigente hoy). */
    List<GuiaHabito> porHabito(HabitoId habitoId);

    Optional<GuiaHabito> byId(GuiaHabitoId id);

    /**
     * La guia mas reciente de este habito que sigue abierta ({@code diaFin IS NULL}), si
     * hay una — la que {@code closePrevious} debe cerrar al dar de alta la siguiente.
     */
    Optional<GuiaHabito> masRecienteAbierta(HabitoId habitoId);
}
