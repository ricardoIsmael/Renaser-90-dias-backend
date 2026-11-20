package com.renaser.os.rocks.api;

import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.FaseDelPrograma;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivoDeLaSemana;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDeLaSemana;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelMes;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDeNoventaDias;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La respuesta de los objetivos de un aprendiz, solo para leer: la comparten {@code GET /api/v1/leadership/people/{personId}/objetivos}
 * (spec 056, FR-027) y {@code GET /api/v1/mentor/groups/{g}/learners/{u}/objetivos} (spec 057, FR-001). Vive en {@code rocks.api} para que
 * los dos módulos la usen sin copiarla.
 *
 * @param noventaDias uno por eje, ordenados por eje; vacío si todavía no los definió
 * @param mes         el mes en curso; null si no hay
 * @param semana      la semana de programa en curso; null si no tiene objetivos semanales
 * @param duracionDias el plazo de la formación de la persona (campo aditivo de la spec 057: las apps viejas lo ignoran)
 * @param fase         la fase en la que va hoy (campo aditivo de la spec 057)
 */
public record ObjetivosDelAprendizResponse(List<NoventaDiasResponse> noventaDias, MesResponse mes, SemanaResponse semana,
                                           int duracionDias, FaseResponse fase) {

    public static ObjetivosDelAprendizResponse from(ObjetivosDelAprendiz objetivos) {
        return new ObjetivosDelAprendizResponse(objetivos.noventaDias().stream().map(NoventaDiasResponse::from).toList(),
                MesResponse.from(objetivos.mes()), SemanaResponse.from(objetivos.semana()), objetivos.duracionDias(),
                FaseResponse.from(objetivos.fase()));
    }

    /** @param numero la fase, de 1 a 4; {@code primerDia} y {@code ultimoDia} los días del programa que abarca, estirados al plazo */
    public record FaseResponse(int numero, int primerDia, int ultimoDia) {

        static FaseResponse from(FaseDelPrograma fase) {
            return new FaseResponse(fase.numero(), fase.primerDia(), fase.ultimoDia());
        }
    }

    /**
     * @param id      el eje: hay un objetivo de 90 días por eje y {@code RocasDelAprendizFinder} no expone otro identificador
     * @param avance  el porcentaje (0 a 100) hacia la meta; null si el objetivo es solo cualitativo, nunca un cero inventado
     * @param valor   lo que lleva hoy hacia la meta; null si es cualitativo
     * @param titulo  la frase del objetivo
     */
    public record NoventaDiasResponse(String id, String eje, String titulo, Integer avance, BigDecimal meta, BigDecimal valor,
                                      String unidad, boolean unidadAdelante, BigDecimal lineaBase, boolean porConfirmar) {

        static NoventaDiasResponse from(ObjetivoDeNoventaDias o) {
            return new NoventaDiasResponse(o.eje(), o.eje(), o.objetivo(), o.porcentaje(), o.meta(), o.avance(), o.unidad(),
                    o.unidadAdelante(), o.lineaBase(), o.porConfirmar());
        }
    }

    /** @param titulo «Mes 2», por ejemplo: cada eje trae el suyo en {@code objetivos} */
    public record MesResponse(String titulo, int numeroMes, int diaDeCierre, List<ObjetivoDelMesResponse> objetivos) {

        static MesResponse from(ObjetivosDelMes mes) {
            return mes == null ? null : new MesResponse("Mes " + mes.numeroMes(), mes.numeroMes(), mes.diaDeCierre(),
                    mes.objetivos().stream().map(ObjetivoDelMesResponse::from).toList());
        }
    }

    /**
     * @param titulo       lo que la persona escribió para ese mes; null si solo tiene la cifra calculada
     * @param cifra        lo que hay que lograr al cierre del mes; null si no hay cifra
     * @param motivoSinCifra por qué no hay cifra, si no la hay
     */
    public record ObjetivoDelMesResponse(String eje, String titulo, BigDecimal cifra, String unidad, boolean unidadAdelante,
                                         boolean metaAlcanzada, String motivoSinCifra) {

        static ObjetivoDelMesResponse from(ObjetivoDelMesDelEje o) {
            return new ObjetivoDelMesResponse(o.eje(), o.tituloPropio(), o.cifra(), o.unidad(), o.unidadAdelante(), o.metaAlcanzada(),
                    o.motivoSinCifra());
        }
    }

    /** @param titulo «Semana 7», por ejemplo: cada eje trae su objetivo en {@code objetivos} */
    public record SemanaResponse(String titulo, int numeroSemana, LocalDate inicio, LocalDate fin,
                                 List<ObjetivoSemanalResponse> objetivos, int planificadas, int cumplidas) {

        static SemanaResponse from(ObjetivosDeLaSemana semana) {
            return semana == null ? null : new SemanaResponse("Semana " + semana.numeroSemana(), semana.numeroSemana(), semana.inicio(),
                    semana.fin(), semana.objetivos().stream().map(ObjetivoSemanalResponse::from).toList(), semana.planificadas(),
                    semana.cumplidas());
        }
    }

    public record ObjetivoSemanalResponse(String eje, String titulo) {

        static ObjetivoSemanalResponse from(ObjetivoDeLaSemana o) {
            return new ObjetivoSemanalResponse(o.eje(), o.titulo());
        }
    }
}
