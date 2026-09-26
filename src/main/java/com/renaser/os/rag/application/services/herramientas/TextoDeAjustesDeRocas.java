package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort.VentanaDeEdicion;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Los textos de {@code proponer_agregar_accion} y {@code proponer_editar_objetivo_semanal} (D-177): el
 * resumen que ve la persona junto a los botones, y lo que se le dice cuando {@code rocks} no deja.
 * Cada rechazo dice el motivo real y lo que SI se puede (D-165).
 */
final class TextoDeAjustesDeRocas {

    static final String DIA_EN_CURSO = "El dia en curso no se reacomoda: la accion se puede agregar a manana o a "
            + "otro dia que quede de esta semana.";

    private TextoDeAjustesDeRocas() {
    }

    static String resumenDeAccion(LocalDate fecha, AccionDelPlan accion) {
        return "Agregar al plan del " + TextoDePlanDeRocas.diaYFecha(fecha) + ", en "
                + TextoDePlanDeRocas.nombreDelEje(accion.eje()) + ": " + accion.titulo() + " (" + franja(accion)
                + "). Va detras de las acciones que ese eje ya tiene ese dia; las demas no se tocan.";
    }

    static String accionAgregada(LocalDate fecha, AgregarAccionAlPlanPort.Resultado.Agregada agregada) {
        return "Accion agregada al " + TextoDePlanDeRocas.diaYFecha(fecha) + ": "
                + TextoDePlanDeRocas.nombreDelEje(agregada.eje()) + " #" + agregada.posicion() + " "
                + agregada.color() + ".";
    }

    static String rechazoDeAccion(AgregarAccionAlPlanPort.Motivo motivo) {
        return switch (motivo) {
            case DIA_EN_CURSO -> DIA_EN_CURSO;
            case FECHA_NO_PLANIFICABLE -> "Ese dia ya no se puede planificar: se puede agregar desde manana hasta el "
                    + "domingo de esta semana.";
            case SIN_OBJETIVO_SEMANAL -> "Ese eje no tiene objetivo esta semana, y las acciones del dia salen de ahi: "
                    + "primero hay que armar el objetivo de la semana de ese eje.";
            case EJE_COMPLETO -> "Ese eje ya tiene sus 3 acciones ese dia. Se puede elegir otro dia, o rehacer el plan "
                    + "de ese dia desde la app.";
            case DIA_COMPLETO -> "Ese dia ya tiene todas las acciones que admite. Se puede elegir otro dia.";
            case DATOS_INVALIDOS -> "No se pudo guardar esa accion: revise que el titulo no este vacio ni sea "
                    + "demasiado largo.";
            case ROCAS_BLOQUEADAS -> "Primero tiene que completar su onboarding (sus Rocas Maestras) para poder "
                    + "planificar.";
            case SIN_PROGRAMA -> "No encontre un programa activo para esta cuenta.";
            case SIN_ACCESO -> "No se pudo guardar: la cuenta esta suspendida o todavia no tiene el programa de "
                    + "rocas activo.";
        };
    }

    static String resumenDeEdicion(int numeroSemana, String eje, EditarObjetivoSemanalPort.Cambio cambio) {
        List<String> partes = new ArrayList<>();
        if (cambio.titulo() != null) {
            partes.add("objetivo: " + cambio.titulo());
        }
        if (cambio.obstaculo() != null) {
            partes.add("obstaculo: " + cambio.obstaculo());
        }
        if (cambio.contingencia() != null) {
            partes.add("contingencia: " + cambio.contingencia());
        }
        if (cambio.autoevaluacionInicio() != null) {
            partes.add("como arranca: " + cambio.autoevaluacionInicio() + "/10");
        }
        return "Cambiar el objetivo de " + TextoDePlanDeRocas.nombreDelEje(eje) + " de la semana " + numeroSemana
                + ". " + String.join("; ", partes) + ". Lo que no se nombra queda como esta.";
    }

    static String rechazoDeEdicion(EditarObjetivoSemanalPort.Motivo motivo, VentanaDeEdicion ventana) {
        return switch (motivo) {
            case VENTANA_CERRADA -> ventanaCerrada(ventana);
            case SIN_OBJETIVO_SEMANAL -> "Ese eje no tiene objetivo en esa semana: se puede crear con el plan de la "
                    + "semana.";
            case DATOS_INVALIDOS -> "No se pudo guardar el cambio: revise que los textos no sean demasiado largos y "
                    + "que como arranca vaya del 1 al 10.";
            case SIN_PROGRAMA -> "No encontre un programa activo para esta cuenta.";
            case SIN_ACCESO -> "No se pudo guardar: la cuenta esta suspendida o todavia no tiene el programa de "
                    + "rocas activo.";
        };
    }

    /** El motivo real (RK-5) y dos salidas que si existen hoy. */
    static String ventanaCerrada(VentanaDeEdicion ventana) {
        return "Ese objetivo semanal ya no se puede cambiar: se corrige en la ventana del Domingo Ritual (domingo "
                + "desde las " + ventana.abreDomingoHora() + ":00 hasta el lunes a las "
                + String.format("%02d", ventana.cierraLunesHora()) + ":00) o, si se creo fuera de esa ventana, "
                + "hasta " + ventana.margenTardioHoras() + " horas despues de crearlo. Lo que si se puede: ajustar "
                + "las acciones de los dias que vienen, o anotar lo que cambio en el cierre de la semana (bloqueo y "
                + "correccion).";
    }

    private static String franja(AccionDelPlan accion) {
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
}
