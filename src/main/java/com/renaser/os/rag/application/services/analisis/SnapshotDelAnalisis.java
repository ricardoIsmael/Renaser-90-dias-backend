package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.ResumenDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;

import java.util.List;

/**
 * La foto de las cifras que un análisis cita (spec 031, FR-006): las del 80/20 del periodo, las de cada semana si es un mes y el resumen
 * del Código Renaser. Se guarda junto al texto (columna {@code cifras}) para que lo que se leyó hoy sea lo que se lee mañana, y es la
 * base de los números que el texto puede nombrar. <b>No lleva texto de la persona</b>: solo cuentas y promedios.
 *
 * @param codigoRenaser {@code null} si hubo menos respuestas que el mínimo: el análisis habla solo de las acciones
 */
public record SnapshotDelAnalisis(CifrasDel8020 cifras, List<ResumenDeSemana> semanas, ResumenDelRadar codigoRenaser) {

    public SnapshotDelAnalisis {
        semanas = semanas == null ? List.of() : List.copyOf(semanas);
    }
}
