package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo que el acompanante necesita de {@code rocks} para ESCRIBIR un plan (2026-09-23): crear el del
 * dia y el de la semana. Solo lo llaman los {@code AccionConfirmable}, o sea despues de que la
 * persona toco "Confirmar" (D-153); una herramienta que ve el modelo nunca llega aca.
 *
 * <p>Todas las reglas (fechas planificables, ventana de las 18:00, de 1 a 3 por eje, un objetivo
 * semanal por eje, Rocas Maestras completas) las corre {@code rocks} con los casos de uso de la
 * app. El rechazo vuelve como {@link ResultadoPlan.Rechazado}, nunca como excepcion.
 */
public interface PlanificarRocasPort {

    /** Los nombres de eje que acepta {@code rocks} ({@code CUERPO}, {@code TRABAJO}, {@code RELACIONES}). */
    List<String> ejesValidos();

    /** Las acciones van en orden de prioridad dentro de cada eje: la primera de cada eje es la VERDE. */
    ResultadoPlan crearPlanDelDia(UserId aprendizId, LocalDate fecha, List<AccionDelPlan> acciones);

    ResultadoPlan crearPlanDeLaSemana(UserId aprendizId, List<ObjetivoSemanal> objetivos);

    /** @param inicio hora local, o {@code null}; @param fin hora local, o {@code null} */
    record AccionDelPlan(String eje, String titulo, LocalTime inicio, LocalTime fin) {
    }

    /** {@code obstaculo} y {@code contingencia} son opcionales ({@code null}). */
    record ObjetivoSemanal(String eje, String titulo, String obstaculo, String contingencia) {
    }

    sealed interface ResultadoPlan {

        record Creado(int cantidad) implements ResultadoPlan {
        }

        record Rechazado(Motivo motivo) implements ResultadoPlan {
        }
    }

    /** Espejo de {@code rocks.api.PlanificacionDeRocasPort.MotivoRechazo}. */
    enum Motivo {
        SIN_PROGRAMA,
        SIN_ACCESO,
        ROCAS_BLOQUEADAS,
        FECHA_NO_PLANIFICABLE,
        SIN_OBJETIVO_SEMANAL,
        YA_PLANIFICADO,
        DATOS_INVALIDOS
    }
}
