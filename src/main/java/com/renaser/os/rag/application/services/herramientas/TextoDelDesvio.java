package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort.HabitoVencido;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.BalanceDelEje;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.DiaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ProgresoDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.SemaforoDelAprendiz;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.Tramo;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El texto de {@code consultar_desvio_de_la_semana} (D-177): hechos, una linea por dato, sin adjetivos.
 * "Se esta alejando" lo dice el modelo si corresponde, siguiendo el prompt; aca no hay ningun umbral.
 */
final class TextoDelDesvio {

    static final String SOLO_HECHOS = "Son hechos, no un juicio: lo de hoy todavia se puede hacer y no cuenta "
            + "como incumplido. Los cambios de horario no son desvio y no se cuentan.";

    private TextoDelDesvio() {
    }

    static String componer(DesvioDeLaSemana desvio) {
        StringBuilder texto = new StringBuilder(encabezado(desvio)).append('\n');
        if (desvio.progreso() != null) {
            texto.append(rocas(desvio.progreso(), desvio.objetivos())).append('\n');
        } else if (desvio.objetivos() != null) {
            texto.append(objetivosSolos(desvio.objetivos())).append('\n');
        }
        if (desvio.vencidos() != null) {
            texto.append(vencidos(desvio.vencidos(), desvio.desde(), desvio.hoy())).append('\n');
        }
        if (desvio.pausados() != null) {
            texto.append(pausados(desvio.pausados())).append('\n');
        }
        semaforo(desvio).ifPresent(linea -> texto.append(linea).append('\n'));
        texto.append(SOLO_HECHOS);
        if (!desvio.faltantes().isEmpty()) {
            texto.append("\nNo pude leer: ").append(String.join(", ", desvio.faltantes()))
                    .append(". No supongas esa parte.");
        }
        return texto.toString();
    }

    private static String encabezado(DesvioDeLaSemana desvio) {
        String semana = desvio.progreso() == null ? "Semana en curso (desde el " + desvio.desde() + ")"
                : "Semana " + desvio.progreso().numeroSemana() + " del programa (" + desvio.progreso().inicio()
                + " al " + desvio.progreso().fin() + ")";
        return semana + ", hoy es " + TextoDePlanDeRocas.diaYFecha(desvio.hoy()) + ". Dias ya terminados: "
                + (desvio.hoy().isAfter(desvio.desde()) ? "del " + desvio.desde() + " al " + desvio.hoy().minusDays(1)
                : "ninguno todavia") + ".";
    }

    private static String rocas(ProgresoDeLaSemana progreso, RocasDeLaSemana objetivos) {
        Map<String, RocaDeLaSemana> porEje = objetivos == null ? Map.of() : objetivos.rocas().stream()
                .collect(Collectors.toMap(RocaDeLaSemana::eje, r -> r, (a, b) -> a, LinkedHashMap::new));
        StringBuilder texto = new StringBuilder("Rocas diarias por eje, en los dias ya terminados:\n");
        progreso.porEje().forEach(balance -> texto.append(lineaDe(balance, porEje.get(balance.eje()), objetivos))
                .append('\n'));
        texto.append(diasSinPlan(progreso)).append(hoyEn(progreso)).append("Avance de la semana: ")
                .append(progreso.progresoSemanalPct()).append("%. Ritmo de los ultimos 7 dias: ")
                .append(progreso.ritmo()).append('.');
        return texto.toString();
    }

    private static String lineaDe(BalanceDelEje balance, RocaDeLaSemana objetivo, RocasDeLaSemana objetivos) {
        StringBuilder linea = new StringBuilder("- ").append(balance.eje());
        if (objetivo != null) {
            linea.append(" (objetivo de la semana: ").append(objetivo.titulo())
                    .append(objetivo.revisada() ? ", ya cerrado" : "").append(')');
        } else if (objetivos != null) {
            linea.append(" (sin objetivo esta semana)");
        }
        if (balance.planificadas() == 0) {
            return linea.append(": sin acciones planificadas").toString();
        }
        int sinCompletar = balance.planificadas() - balance.completadas();
        return linea.append(": ").append(balance.completadas()).append(" completada(s) y ").append(sinCompletar)
                .append(" sin completar, de ").append(balance.planificadas()).append(" planificada(s)").toString();
    }

    private static String diasSinPlan(ProgresoDeLaSemana progreso) {
        List<String> sinPlan = progreso.dias().stream()
                .filter(dia -> dia.fecha().isBefore(progreso.hoy()) && dia.total() == null)
                .map(dia -> TextoDePlanDeRocas.diaYFecha(dia.fecha())).toList();
        return sinPlan.isEmpty() ? "" : "Dias terminados sin rocas planificadas: " + String.join(", ", sinPlan) + ".\n";
    }

    private static String hoyEn(ProgresoDeLaSemana progreso) {
        Optional<DiaDeLaSemana> hoy = progreso.dias().stream().filter(DiaDeLaSemana::esHoy).findFirst();
        if (hoy.isEmpty()) {
            return "";
        }
        if (hoy.get().total() == null) {
            return "Hoy: sin rocas planificadas.\n";
        }
        return "Hoy (todavia en curso): " + hoy.get().completadas() + " de " + hoy.get().total() + " completadas.\n";
    }

    private static String objetivosSolos(RocasDeLaSemana objetivos) {
        if (objetivos.rocas().isEmpty()) {
            return "Objetivos de la semana: todavia no armo ninguno.";
        }
        return "Objetivos de la semana: " + objetivos.rocas().stream()
                .map(r -> r.eje() + " " + r.titulo() + (r.revisada() ? " (ya cerrado)" : ""))
                .collect(Collectors.joining("; ")) + ".";
    }

    private static String vencidos(List<HabitoVencido> vencidos, LocalDate desde, LocalDate hoy) {
        if (!hoy.isAfter(desde)) {
            return "Habitos vencidos sin cumplir: todavia no hay dias terminados esta semana.";
        }
        if (vencidos.isEmpty()) {
            return "Habitos vencidos sin cumplir en los dias terminados: ninguno.";
        }
        Map<String, List<LocalDate>> porHabito = vencidos.stream().collect(Collectors.groupingBy(
                HabitoVencido::titulo, LinkedHashMap::new, Collectors.mapping(HabitoVencido::fecha, Collectors.toList())));
        StringBuilder texto = new StringBuilder("Habitos vencidos sin cumplir en los dias terminados: ")
                .append(vencidos.size()).append('\n');
        porHabito.forEach((titulo, fechas) -> texto.append("- ").append(titulo).append(": ").append(fechas.size())
                .append(" vez/veces (").append(fechas.stream().map(LocalDate::toString)
                        .collect(Collectors.joining(", "))).append(")\n"));
        return texto.toString().stripTrailing();
    }

    private static String pausados(List<HabitoDelPlan> pausados) {
        if (pausados.isEmpty()) {
            return "Habitos en pausa hoy: ninguno.";
        }
        return "Habitos en pausa hoy (no se le piden, no son incumplimiento): " + pausados.stream()
                .map(h -> h.titulo() + (h.pausadoHasta() == null ? " (sin fecha de fin)" : " (hasta el "
                        + h.pausadoHasta() + ")"))
                .collect(Collectors.joining(", ")) + ".";
    }

    private static Optional<String> semaforo(DesvioDeLaSemana desvio) {
        if (desvio.semaforoNoAplica()) {
            return Optional.of("Semaforo: no se le mide.");
        }
        SemaforoDelAprendiz semaforo = desvio.semaforo();
        if (semaforo == null) {
            return Optional.empty();
        }
        String linea = "Semaforo de cumplimiento, ultimos 7 dias cerrados " + tramo(semaforo.vigente());
        if (semaforo.ultimaSemana() != null) {
            linea += " Ultima semana cerrada del semaforo (de sabado a viernes) " + tramo(semaforo.ultimaSemana());
        }
        return Optional.of(linea);
    }

    private static String tramo(Tramo tramo) {
        String cuanto = tramo.porcentaje() == null ? "sin datos"
                : tramo.porcentaje().stripTrailingZeros().toPlainString() + "%, " + tramo.etiqueta();
        return "(" + tramo.desde() + " al " + tramo.hasta() + "): " + cuanto + ", con " + tramo.diasConDatos()
                + " dia(s) con datos.";
    }
}
