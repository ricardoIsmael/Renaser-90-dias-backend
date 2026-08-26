package com.renaser.os.habits.application.ports.out.guia;

import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.util.Collection;
import java.util.List;

public interface LoadGuiaHabitoPort {

    /** UNA sola consulta para N habitos — proyeccion de lectura (hueco #10), nunca N+1. */
    List<GuiaHabito> porHabitos(Collection<HabitoId> habitoIds);
}
