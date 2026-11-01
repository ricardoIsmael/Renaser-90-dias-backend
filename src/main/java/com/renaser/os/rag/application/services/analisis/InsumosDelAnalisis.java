package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rocks.api.Pareto8020Finder.InsumosDel8020;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;

import java.time.ZoneId;
import java.util.List;

/**
 * Todo lo que se junta de un periodo antes de pedir el análisis (spec 031): el 80/20 con lo que la persona dijo al cerrar sus días, las
 * semanas del mes, y lo que respondió en el Código Renaser con su resumen. <b>Es texto personal</b>: nadie lo loguea.
 *
 * @param radar {@code null} si hubo menos respuestas que el mínimo
 */
record InsumosDelAnalisis(PeriodoDelPrograma periodo, ZoneId zona, InsumosDel8020 acciones, List<ResumenDeSemana> semanas,
                          List<RespuestaDelRadar> respuestas, ResumenDelRadar radar) {

    InsumosDelAnalisis {
        semanas = List.copyOf(semanas);
        respuestas = List.copyOf(respuestas);
    }
}
