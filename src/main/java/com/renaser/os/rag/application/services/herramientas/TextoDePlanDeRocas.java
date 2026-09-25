package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.Motivo;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Los textos de {@code proponer_plan_del_dia} y {@code proponer_plan_de_la_semana} (2026-09-23): el
 * resumen que ve la persona junto a los botones y lo que vuelve al confirmar.
 *
 * <p>El resumen dice EXACTAMENTE que se va a guardar: cada eje con sus acciones en orden y sus
 * horas. Es lo unico que la persona lee antes de tocar "Confirmar".
 */
final class TextoDePlanDeRocas {

    static final String NO_ESTA_HECHO = " TODAVIA NO esta guardado: la persona tiene que tocar Confirmar en la "
            + "app para que se guarde. No digas que ya quedo hecho; dile que confirme con el boton.";

    private static final String[] DIAS = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};

    private TextoDePlanDeRocas() {
    }

    /** "Crear el plan del jueves 2026-09-24. Cuerpo: 1) Caminar (06:00 a 06:30); 2) Pesarme (sin hora)." */
    static String resumenDelDia(LocalDate fecha, List<AccionDelPlan> acciones) {
        Map<String, List<AccionDelPlan>> porEje = acciones.stream()
                .collect(Collectors.groupingBy(AccionDelPlan::eje, LinkedHashMap::new, Collectors.toList()));
        List<String> ejes = new ArrayList<>();
        porEje.forEach((eje, delEje) -> ejes.add(nombreDelEje(eje) + ": " + enumeradas(delEje)));
        return "Crear el plan del " + diaYFecha(fecha) + ". " + String.join(". ", ejes) + ".";
    }

    static String resumenDeLaSemana(List<ObjetivoSemanal> objetivos) {
        return "Crear los objetivos de la semana. " + objetivos.stream().map(TextoDePlanDeRocas::lineaDe)
                .collect(Collectors.joining(". ")) + ". Si algun eje ya tiene objetivo esa semana, se deja como esta.";
    }

    static String diaYFecha(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha;
    }

    /** Lo que se le dice cuando {@code rocks} rechaza el plan del dia al confirmar. */
    static String rechazoDelDia(Motivo motivo) {
        return switch (motivo) {
            case FECHA_NO_PLANIFICABLE -> "Ese dia ya no se puede planificar. Elige uno que quede de la semana.";
            case SIN_OBJETIVO_SEMANAL -> "Primero tiene que armar su plan de la semana: las acciones del dia salen de ahi.";
            case YA_PLANIFICADO -> "El dia en curso ya esta armado y no se reacomoda. Puede cambiar los que vienen.";
            case DATOS_INVALIDOS -> "No se pudo guardar ese plan: revise que sean de 1 a 3 acciones por eje y que "
                    + "los titulos no sean demasiado largos.";
            default -> rechazoComun(motivo);
        };
    }

    /** Lo que se le dice cuando {@code rocks} rechaza el plan de la semana al confirmar. */
    static String rechazoDeLaSemana(Motivo motivo) {
        return switch (motivo) {
            case YA_PLANIFICADO -> "Esa semana ya tiene objetivo en todos los ejes que pidio. Un objetivo ya "
                    + "guardado se cambia editandolo desde la app.";
            case DATOS_INVALIDOS -> "No se pudo guardar: revise que haya un solo objetivo por eje y que los textos "
                    + "no sean demasiado largos.";
            default -> rechazoComun(motivo);
        };
    }

    private static String rechazoComun(Motivo motivo) {
        return switch (motivo) {
            case SIN_PROGRAMA -> "No encontre un programa activo para esta cuenta.";
            case ROCAS_BLOQUEADAS -> "Primero tiene que completar su onboarding (sus Rocas Maestras) para poder "
                    + "planificar.";
            default -> "No se pudo guardar el plan: la cuenta esta suspendida o todavia no tiene el programa de "
                    + "rocas activo.";
        };
    }

    private static String enumeradas(List<AccionDelPlan> delEje) {
        List<String> lineas = new ArrayList<>();
        for (AccionDelPlan accion : delEje) {
            lineas.add((lineas.size() + 1) + ") " + accion.titulo() + " (" + franjaDe(accion) + ")");
        }
        return String.join("; ", lineas);
    }

    private static String franjaDe(AccionDelPlan accion) {
        String inicio = accion.inicio() == null ? null : accion.inicio().format(PlanDeRocasJson.HORA);
        String fin = accion.fin() == null ? null : accion.fin().format(PlanDeRocasJson.HORA);
        if (inicio != null && fin != null) {
            return inicio + " a " + fin;
        }
        if (inicio != null) {
            return "desde " + inicio;
        }
        return fin != null ? "hasta " + fin : "sin hora";
    }

    private static String lineaDe(ObjetivoSemanal objetivo) {
        StringBuilder linea = new StringBuilder(nombreDelEje(objetivo.eje())).append(": ").append(objetivo.titulo());
        if (objetivo.obstaculo() != null) {
            linea.append(" (obstaculo: ").append(objetivo.obstaculo()).append(')');
        }
        if (objetivo.contingencia() != null) {
            linea.append(" (contingencia: ").append(objetivo.contingencia()).append(')');
        }
        return linea.toString();
    }

    /** {@code CUERPO} -> {@code Cuerpo}. De paquete: tambien lo usa {@link TextoDeCierreDeSemana}. */
    static String nombreDelEje(String eje) {
        String minusculas = eje.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(minusculas.charAt(0)) + minusculas.substring(1);
    }
}
