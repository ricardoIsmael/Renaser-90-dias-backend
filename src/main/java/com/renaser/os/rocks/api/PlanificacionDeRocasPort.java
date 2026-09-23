package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Contrato publico de {@code rocks} para CREAR el plan del dia y el de la semana desde otro modulo
 * (2026-09-23, herramientas {@code proponer_plan_del_dia} y {@code proponer_plan_de_la_semana} del
 * acompanante, {@code rag}). Es la mitad de escritura de {@link RocasDelAprendizFinder}.
 *
 * <p><b>Delega, no reimplementa.</b> Cada metodo corre el MISMO caso de uso que
 * {@code POST /rocks/plan} y {@code POST /rocks/weekly}: la zona del participante, que fechas se
 * pueden planificar, la ventana de las 18:00, el reemplazo de un dia que todavia no llego, de 1 a 3
 * acciones por eje, un objetivo semanal por eje y las Rocas Maestras completas se deciden ahi, en
 * un solo lugar. Aca no hay ninguna regla.
 *
 * <p><b>Un rechazo del negocio vuelve como {@link ResultadoPlanificacion.Rechazado}, no como
 * excepcion.</b> Los codigos que {@code rocks} escribe en sus mensajes ({@code INVALID_DATE},
 * {@code NO_WEEKLY_ROCK}, {@code ALREADY_PLANNED}, {@code ROCKS_LOCKED}) son internos del modulo:
 * los traduce {@code rocks} a {@link MotivoRechazo} para que ningun otro modulo tenga que leer
 * el texto de una excepcion ajena. Un error que no es un rechazo (la base caida) si sube.
 *
 * <p>Lo que cruza la frontera son tipos de Java: el eje viaja como el nombre de la constante de
 * {@code EjeObjetivo}, nunca el enum.
 */
public interface PlanificacionDeRocasPort {

    /**
     * Los nombres validos de eje, en el orden de {@code EjeObjetivo}. Estan aca para que quien
     * llama pueda rechazar un eje inventado sin importar el dominio de {@code rocks};
     * {@code PlanificacionDeRocasServiceTest} rompe si dejan de coincidir con el enum.
     */
    List<String> EJES = List.of("CUERPO", "TRABAJO", "RELACIONES");

    /** Mismo efecto que {@code POST /rocks/plan} con esa fecha y esas acciones. */
    ResultadoPlanificacion crearPlanDelDia(UserId aprendizId, LocalDate fecha, List<AccionDelDia> acciones);

    /** Mismo efecto que {@code POST /rocks/weekly}: los ejes que ya tienen objetivo esa semana se dejan como estan. */
    ResultadoPlanificacion crearPlanDeLaSemana(UserId aprendizId, List<ObjetivoDeLaSemana> objetivos);

    /**
     * Un objetivo del dia, tal como lo recibe {@code CrearPlanDiarioUseCase.ItemRocaDiaria}.
     *
     * @param posicion   1, 2 o 3 dentro de su eje; decide el color Pareto (la 1 es la VERDE)
     * @param horaInicio hora local; {@code null} si no se fijo
     * @param horaFin    hora local; {@code null} si no se fijo
     */
    record AccionDelDia(String eje, int posicion, String titulo, int puntajeImpacto, boolean esDelegable,
                        LocalTime horaInicio, LocalTime horaFin) {
    }

    /** Un objetivo semanal, tal como lo recibe {@code CrearPlanSemanalUseCase.ItemRocaSemanal}. */
    record ObjetivoDeLaSemana(String eje, String titulo, String obstaculo, String contingencia) {
    }

    sealed interface ResultadoPlanificacion {

        /** @param cantidad cuantas rocas se guardaron (en la semana, puede ser menos de las pedidas) */
        record Creado(int cantidad) implements ResultadoPlanificacion {
        }

        record Rechazado(MotivoRechazo motivo) implements ResultadoPlanificacion {
        }
    }

    /** Por que {@code rocks} no guardo el plan. */
    enum MotivoRechazo {
        /** El participante no existe. */
        SIN_PROGRAMA,
        /** Cuenta suspendida, o sin el programa de rocas andando. */
        SIN_ACCESO,
        /** {@code ROCKS_LOCKED}: le faltan Rocas Maestras (onboarding). */
        ROCAS_BLOQUEADAS,
        /** {@code INVALID_DATE}: esa fecha no se puede planificar ahora. */
        FECHA_NO_PLANIFICABLE,
        /** {@code NO_WEEKLY_ROCK}: el eje no tiene objetivo semanal para esa semana. */
        SIN_OBJETIVO_SEMANAL,
        /** {@code ALREADY_PLANNED}: el dia en curso ya tiene plan, o la semana ya tiene todos los ejes pedidos. */
        YA_PLANIFICADO,
        /** Cualquier otra validacion del comando o del dominio (cantidades, posiciones, largos, eje). */
        DATOS_INVALIDOS
    }
}
