package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.CierreDeLaSemanaAnterior;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.CierreDelEje;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.DiaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ObjetivoDeNoventaDias;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ProgresoDeLaSemana;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Los textos de {@code consultar_rocas} con alcance {@code progreso} y {@code noventa}, y el cierre de
 * la semana anterior que se suma a {@code semana} (D-177). Mismo criterio que {@link TextoDeRocas}: una
 * linea por dato, escrita para que el modelo la parafrasee, y ningun numero calculado aca.
 */
final class TextoDeObjetivos {

    private TextoDeObjetivos() {
    }

    static String progreso(ProgresoDeLaSemana progreso) {
        StringBuilder texto = new StringBuilder("Progreso de la semana ").append(progreso.numeroSemana())
                .append(" del programa ").append(TextoDePlanDeRocas.rangoDeLaSemana(progreso.inicio(), progreso.fin()))
                .append(", hoy es ").append(TextoDePlanDeRocas.diaYFecha(progreso.hoy())).append(":\n")
                .append("Avance de la semana: ").append(progreso.progresoSemanalPct())
                .append("% de las rocas planificadas en los dias que ya pasaron, hoy incluido.\n");
        progreso.dias().forEach(dia -> texto.append(lineaDe(dia)).append('\n'));
        texto.append("Ritmo de los ultimos 7 dias (sin contar hoy): ").append(progreso.ritmo()).append(", con ")
                .append(progreso.diasCompletadosUltimos7()).append(" dia(s) con al menos una roca completada.\n")
                .append(TextoDeRocas.lineaDe(progreso.planDeManana()));
        if (progreso.planificacionBloqueada()) {
            texto.append("\nPlanificar manana ya no es opcional: es tarde y todavia no tiene las 3 rocas de manana.");
        }
        return texto.toString();
    }

    static String noventa(List<ObjetivoDeNoventaDias> objetivos) {
        if (objetivos.isEmpty()) {
            return "Todavia no definio sus objetivos de los 90 dias (Rocas Maestras).";
        }
        StringBuilder texto = new StringBuilder("Objetivos de los 90 dias (Rocas Maestras), por eje:\n");
        objetivos.forEach(objetivo -> texto.append(lineaDe(objetivo)).append('\n'));
        return texto.toString().trim();
    }

    /** Lo que se agrega al final de {@code semana}; vacio en la semana 1. */
    static String cierreAnterior(Optional<CierreDeLaSemanaAnterior> cierre) {
        if (cierre.isEmpty()) {
            return "";
        }
        CierreDeLaSemanaAnterior anterior = cierre.get();
        StringBuilder texto = new StringBuilder("\nCierre de la semana ").append(anterior.numeroSemana())
                .append(" (la anterior):\n");
        if (anterior.ejes().isEmpty()) {
            return texto.append("Esa semana no tuvo objetivos.").toString();
        }
        anterior.ejes().forEach(eje -> texto.append(lineaDe(eje)).append('\n'));
        return texto.toString().stripTrailing();
    }

    private static String lineaDe(DiaDeLaSemana dia) {
        String cuando = "- " + TextoDePlanDeRocas.diaYFecha(dia.fecha()) + (dia.esHoy() ? " (hoy)" : "") + ": ";
        if (dia.completadas() == null) {
            return cuando + "todavia no llega";
        }
        if (dia.total() == null) {
            return cuando + "sin rocas planificadas";
        }
        return cuando + dia.completadas() + " de " + dia.total() + " completadas";
    }

    private static String lineaDe(ObjetivoDeNoventaDias objetivo) {
        StringBuilder linea = new StringBuilder("- ").append(objetivo.eje()).append(" | ").append(objetivo.objetivo());
        if (objetivo.meta() == null) {
            return linea.append(" | sin meta numerica").toString();
        }
        linea.append(" | meta=").append(cifra(objetivo, objetivo.meta()));
        if (objetivo.lineaBase() != null) {
            linea.append(" | punto de partida=").append(cifra(objetivo, objetivo.lineaBase()));
        }
        return linea.append(" | lleva=").append(cifra(objetivo, objetivo.avance()))
                .append(" | avance=").append(objetivo.porcentaje()).append("% del camino").toString();
    }

    private static String lineaDe(CierreDelEje eje) {
        StringBuilder linea = new StringBuilder("- ").append(eje.eje()).append(" | ").append(eje.titulo());
        if (eje.autoevaluacionInicio() != null) {
            linea.append(" | arranco en ").append(eje.autoevaluacionInicio()).append("/10");
        }
        if (eje.autoevaluacionFin() == null) {
            return linea.append(" | no se cerro").toString();
        }
        return linea.append(" | cerro en ").append(eje.autoevaluacionFin()).append("/10")
                .append(" | bloqueo principal=").append(eje.bloqueoPrincipal())
                .append(" | correccion para esta semana=").append(eje.correccion()).toString();
    }

    private static String cifra(ObjetivoDeNoventaDias objetivo, BigDecimal valor) {
        String numero = valor.stripTrailingZeros().toPlainString();
        return objetivo.unidadAdelante() ? objetivo.unidad() + " " + numero : numero + " " + objetivo.unidad();
    }
}
