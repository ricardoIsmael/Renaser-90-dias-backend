package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder;
import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rocks.api.Pareto8020Finder;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.InsumosDel8020;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Junta lo que lee el análisis de un periodo, en el orden de siempre y cada cosa de donde vive (spec 031, restricción 1: nada se copia):
 * el 80/20 y «Cierra tu día» vienen de {@code rocks}, las respuestas del Código Renaser de {@code habits}. Las lecturas son transacciones
 * cortas de cada módulo; este objeto no abre ninguna, para que la IA no corra nunca dentro de una (C-1).
 */
@Component
class ReunirInsumosDelAnalisis {

    private final Pareto8020Finder acciones;
    private final PeriodosDelProgramaFinder periodos;
    private final RespuestasDelRadarFinder radar;

    ReunirInsumosDelAnalisis(Pareto8020Finder acciones, PeriodosDelProgramaFinder periodos, RespuestasDelRadarFinder radar) {
        this.acciones = acciones;
        this.periodos = periodos;
        this.radar = radar;
    }

    InsumosDelAnalisis reunir(UserId persona, PeriodoDelPrograma periodo, LocalDate primerDia, ZoneId zona, ReglasDelAnalisis reglas) {
        InsumosDel8020 delPeriodo = acciones.insumos(persona, periodo.inicio(), periodo.fin());
        List<PeriodoDelPrograma> semanasDelMes = periodo.tipo() == Tipo.MES ? periodos.semanasDelMes(primerDia, periodo) : List.of();
        List<RespuestaDelRadar> respuestas = radar.entre(persona, periodo.inicio().atStartOfDay(zona).toInstant(),
                periodo.fin().plusDays(1).atStartOfDay(zona).toInstant());
        ResumenDelRadar resumen = ResumenDelCodigoRenaser.resumir(respuestas, zona, reglas.minimoDeRespuestasDelRadar(), semanasDelMes);
        return new InsumosDelAnalisis(periodo, zona, delPeriodo, resumenesDe(persona, semanasDelMes), respuestas, resumen);
    }

    /** Las cifras del periodo, sin las notas de cierre ni el Código Renaser: lo que la hoja muestra mientras el periodo está abierto. */
    CifrasDel8020 cifrasDe(UserId persona, PeriodoDelPrograma periodo) {
        return acciones.cifras(persona, periodo.inicio(), periodo.fin());
    }

    List<ResumenDeSemana> resumenesDe(UserId persona, List<PeriodoDelPrograma> semanas) {
        return semanas.stream().map(semana -> {
            CifrasDel8020 cifras = acciones.cifras(persona, semana.inicio(), semana.fin());
            return new ResumenDeSemana(semana.numero(), cifras.planificadas(), cifras.cumplidas(), cifras.porcentajeCumplidas(),
                    cifras.verdes().porcentajeDelImpacto());
        }).toList();
    }
}
