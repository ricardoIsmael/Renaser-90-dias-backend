package com.renaser.os.habits.application.ports.out.audiencia;

import com.renaser.os.habits.domain.model.audiencia.HistorialDeAudiencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.util.Collection;
import java.util.Map;

/** Las versiones de audiencia de varios hábitos en UNA consulta (la generación diaria recorre todo el padrón). */
public interface CargarAudienciasPort {

    /** Los hábitos sin versiones no aparecen en el mapa: quien llama los trata como "para todos". */
    Map<HabitoId, HistorialDeAudiencia> deHabitos(Collection<HabitoId> habitos);
}
