package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.CeldaDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifra;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifras;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.DetalleDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvidenciaDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FilaDePilar;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FocoDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.HabitoDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.PalabraDeLaPersona;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.TablaDePilares;
import com.renaser.os.leadership.domain.model.documento.NombreDelPilar;
import com.renaser.os.mentoring.api.ObligacionDelDia;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Las cuentas de la semana de una persona para su documento (spec 056, FR-035): las cuatro cifras, la tabla de pilares por día, las
 * evidencias, lo que escribió, el pilar que pide foco y el detalle día por día. Es una función pura de {@link SemanaDeLaPersona} —la misma
 * semana que ve el mentor en su ficha (spec 055)—: no lee nada ni decide quién puede ver qué.
 *
 * <p>«Cumplido» es un hábito {@code COMPLETADO}. Un día está «programado» si tiene al menos un hábito y está «activo» si cumplió al menos uno.
 * Los pilares salen en el orden de la ficha del mentor —Cuerpo, Mente, Espíritu, Emociones— y los de otra formación, después.
 */
final class ResumenDeLaSemana {

    private static final String COMPLETADO = "COMPLETADO";
    private static final List<String> ORDEN_DE_LOS_PILARES = List.of("Cuerpo", "Mente", "Espíritu", "Emociones");

    private ResumenDeLaSemana() {
    }

    /** @param anterior la semana anterior; null si es anterior al Día 1 y no hay con qué comparar */
    static Cifras cifras(SemanaDeLaPersona actual, SemanaDeLaPersona anterior) {
        return new Cifras(cifra(actual, anterior, ResumenDeLaSemana::habitosCumplidos), cifra(actual, anterior, ResumenDeLaSemana::diasActivos),
                cifra(actual, anterior, ResumenDeLaSemana::evidencias), cifra(actual, anterior, ResumenDeLaSemana::diasProgramados));
    }

    private static Cifra cifra(SemanaDeLaPersona actual, SemanaDeLaPersona anterior, ToIntFunction<SemanaDeLaPersona> medida) {
        return new Cifra(medida.applyAsInt(actual), anterior == null ? null : medida.applyAsInt(anterior));
    }

    private static int habitosCumplidos(SemanaDeLaPersona semana) {
        return (int) semana.dias().stream().flatMap(dia -> dia.obligaciones().stream()).filter(ResumenDeLaSemana::cumplida).count();
    }

    private static int diasActivos(SemanaDeLaPersona semana) {
        return (int) semana.dias().stream().filter(dia -> dia.obligaciones().stream().anyMatch(ResumenDeLaSemana::cumplida)).count();
    }

    private static int evidencias(SemanaDeLaPersona semana) {
        return semana.dias().stream().flatMap(dia -> dia.obligaciones().stream()).mapToInt(obligacion -> obligacion.evidencias().size()).sum();
    }

    static int diasProgramados(SemanaDeLaPersona semana) {
        return (int) semana.dias().stream().filter(dia -> !dia.obligaciones().isEmpty()).count();
    }

    private static boolean cumplida(ObligacionDelDia obligacion) {
        return COMPLETADO.equals(obligacion.estadoHabito());
    }

    static TablaDePilares tabla(SemanaDeLaPersona semana) {
        Map<String, List<CeldaDelDia>> porPilar = new LinkedHashMap<>();
        for (DiaDeLaFicha dia : semana.dias()) {
            for (ObligacionDelDia obligacion : dia.obligaciones()) {
                porPilar.computeIfAbsent(NombreDelPilar.de(obligacion.pilar()), pilar -> new ArrayList<>());
            }
        }
        List<FilaDePilar> filas = porPilar.keySet().stream().sorted(ordenDeLosPilares())
                .map(pilar -> new FilaDePilar(pilar, semana.dias().stream().map(dia -> celda(dia, pilar)).toList())).toList();
        return new TablaDePilares(semana.dias().stream().map(DiaDeLaFicha::fecha).toList(), filas);
    }

    private static Comparator<String> ordenDeLosPilares() {
        return Comparator.<String>comparingInt(pilar -> ORDEN_DE_LOS_PILARES.contains(pilar) ? ORDEN_DE_LOS_PILARES.indexOf(pilar)
                : ORDEN_DE_LOS_PILARES.size()).thenComparing(Comparator.naturalOrder());
    }

    private static CeldaDelDia celda(DiaDeLaFicha dia, String pilar) {
        List<ObligacionDelDia> delPilar = dia.obligaciones().stream().filter(o -> NombreDelPilar.de(o.pilar()).equals(pilar)).toList();
        return new CeldaDelDia((int) delPilar.stream().filter(ResumenDeLaSemana::cumplida).count(), delPilar.size());
    }

    static List<EvidenciaDeLaSemana> evidencias(SemanaDeLaPersona semana, ZoneId zona) {
        List<EvidenciaDeLaSemana> evidencias = new ArrayList<>();
        for (DiaDeLaFicha dia : semana.dias()) {
            for (ObligacionDelDia obligacion : dia.obligaciones()) {
                obligacion.evidencias().forEach(evidencia -> evidencias.add(new EvidenciaDeLaSemana(evidencia.tipo(),
                        evidencia.subidaEn().atZone(zona).toLocalDate(), obligacion.revision(), obligacion.titulo())));
            }
        }
        return evidencias;
    }

    /** Lo que la persona escribió al cumplir sus hábitos; no trae a quien no escribió nada. */
    static List<PalabraDeLaPersona> palabras(SemanaDeLaPersona semana) {
        List<PalabraDeLaPersona> palabras = new ArrayList<>();
        for (DiaDeLaFicha dia : semana.dias()) {
            dia.obligaciones().stream().filter(o -> o.respuestaTexto() != null && !o.respuestaTexto().isBlank())
                    .forEach(o -> palabras.add(new PalabraDeLaPersona(dia.fecha(), o.titulo(), o.respuestaTexto().trim())));
        }
        return palabras;
    }

    /**
     * El pilar en que más faltó, con lo que cumplió; si todos fueron completos, sostener el ritmo (pilar null); null si la semana no tuvo nada
     * programado. Ante un empate, el primero en el orden de la ficha.
     */
    static FocoDeLaSemana foco(TablaDePilares tabla) {
        FocoDeLaSemana peor = null;
        for (FilaDePilar fila : tabla.filas()) {
            int cumplidos = fila.celdas().stream().mapToInt(CeldaDelDia::cumplidos).sum();
            int programados = fila.celdas().stream().mapToInt(CeldaDelDia::programados).sum();
            if (cumplidos < programados && (peor == null || cumplidos * peor.programados() < peor.cumplidos() * programados)) {
                peor = new FocoDeLaSemana(fila.pilar(), cumplidos, programados);
            }
        }
        if (peor != null) {
            return peor;
        }
        return tabla.filas().isEmpty() ? null : new FocoDeLaSemana(null, 0, 0);
    }

    static List<DetalleDelDia> detalle(SemanaDeLaPersona semana) {
        return semana.dias().stream().map(dia -> new DetalleDelDia(dia.fecha(), dia.diaDePrograma(), dia.motivo(), dia.obligaciones().stream()
                .map(o -> new HabitoDelDia(NombreDelPilar.de(o.pilar()), o.titulo(), o.estadoHabito(), o.entrega())).toList())).toList();
    }
}
