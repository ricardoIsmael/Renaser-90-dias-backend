package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * Lo que el acompanante necesita de {@code rocks} para SUMAR una accion a un dia que viene sin tocar
 * las demas (D-177, {@code proponer_agregar_accion}). Solo lo llama {@code AgregarAccionConfirmable},
 * despues de que la persona toco "Confirmar" (D-153).
 *
 * <p>Las reglas (dia en curso, ventana de fechas, objetivo semanal, cupo por eje y por dia) las corre
 * {@code rocks} con {@code AgregarRocaDiariaUseCase}. El rechazo vuelve como {@link Resultado.Rechazado}.
 */
public interface AgregarAccionAlPlanPort {

    /** La accion tiene la misma forma que una del plan del dia: eje, titulo y horas locales opcionales. */
    Resultado agregar(UserId aprendizId, LocalDate fecha, AccionDelPlan accion);

    sealed interface Resultado {

        /** @param color el color Pareto que le toco por la posicion ({@code VERDE}, {@code AMARILLA}, {@code ROJA}) */
        record Agregada(String eje, int posicion, String color) implements Resultado {
        }

        record Rechazado(Motivo motivo) implements Resultado {
        }
    }

    /** Espejo de {@code rocks.api.AgregarAccionAlDiaPort.MotivoRechazo}. */
    enum Motivo {
        SIN_PROGRAMA,
        SIN_ACCESO,
        ROCAS_BLOQUEADAS,
        DIA_EN_CURSO,
        FECHA_NO_PLANIFICABLE,
        SIN_OBJETIVO_SEMANAL,
        EJE_COMPLETO,
        DIA_COMPLETO,
        DATOS_INVALIDOS
    }
}
