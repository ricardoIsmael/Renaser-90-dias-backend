package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Contrato publico de {@code rocks} para SUMAR una accion a un dia que todavia no llego, sin perder las
 * que ya tiene (D-177, herramienta {@code proponer_agregar_accion} del acompanante, {@code rag}).
 *
 * <p><b>Por que no alcanza {@link PlanificacionDeRocasPort#crearPlanDelDia}.</b> Aquel reemplaza el dia
 * entero; reenviarlo desde el chat perderia la descripcion, las acciones internas y el puntaje de las
 * acciones que la persona escribio en la app. Este corre {@code AgregarRocaDiariaUseCase}, que inserta
 * una sola fila en la primera posicion libre de su eje.
 *
 * <p>Mismo criterio que {@link PlanificacionDeRocasPort}: delega, los rechazos del negocio vuelven como
 * {@link ResultadoAgregado.Rechazado} y el eje cruza como el nombre de la constante.
 */
public interface AgregarAccionAlDiaPort {

    ResultadoAgregado agregar(UserId aprendizId, LocalDate fecha, AccionNueva accion);

    /**
     * @param eje        nombre de la constante de {@code EjeObjetivo} ({@link PlanificacionDeRocasPort#EJES})
     * @param horaInicio hora local; {@code null} si no se fijo
     * @param horaFin    hora local; {@code null} si no se fijo
     */
    record AccionNueva(String eje, String titulo, int puntajeImpacto, boolean esDelegable, LocalTime horaInicio,
                       LocalTime horaFin) {
    }

    sealed interface ResultadoAgregado {

        /** @param color el color Pareto que le toco por su posicion ({@code VERDE}, {@code AMARILLA}, {@code ROJA}) */
        record Agregada(String eje, int posicion, String color) implements ResultadoAgregado {
        }

        record Rechazado(MotivoRechazo motivo) implements ResultadoAgregado {
        }
    }

    /** Por que {@code rocks} no agrego la accion. */
    enum MotivoRechazo {
        /** El participante no existe. */
        SIN_PROGRAMA,
        /** Cuenta suspendida, o sin el programa de rocas andando. */
        SIN_ACCESO,
        /** {@code ROCKS_LOCKED}: le faltan Rocas Maestras (onboarding). */
        ROCAS_BLOQUEADAS,
        /** {@code CURRENT_DAY}: es el dia en curso, que no se reacomoda. */
        DIA_EN_CURSO,
        /** {@code INVALID_DATE}: ya paso, o cae despues del domingo de esta semana de programa. */
        FECHA_NO_PLANIFICABLE,
        /** {@code NO_WEEKLY_ROCK}: el eje no tiene objetivo semanal en la semana de esa fecha. */
        SIN_OBJETIVO_SEMANAL,
        /** {@code AXIS_FULL}: ese eje ya tiene sus 3 acciones ese dia. */
        EJE_COMPLETO,
        /** {@code DAY_FULL}: el dia ya tiene 9 acciones. */
        DIA_COMPLETO,
        /** Titulo vacio o demasiado largo, puntaje fuera de escala, eje inexistente. */
        DATOS_INVALIDOS
    }
}
