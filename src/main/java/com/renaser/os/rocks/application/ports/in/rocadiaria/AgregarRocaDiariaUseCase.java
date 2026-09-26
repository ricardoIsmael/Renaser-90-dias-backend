package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Agrega UNA accion a un dia que todavia no llego, sin tocar las que ese dia ya tiene (D-177,
 * herramienta {@code proponer_agregar_accion} del acompanante).
 *
 * <p><b>Por que no se reusa {@link CrearPlanDiarioUseCase}.</b> Aquel REEMPLAZA el dia entero de un
 * dia futuro: para sumar una accion habria que reenviarlo completo, y lo que el chat no ve (la
 * descripcion, las acciones internas, el puntaje de impacto de cada una) se perderia en silencio.
 * Este inserta una fila y no lee ni escribe las demas.
 *
 * <p>Reglas, todas ya existentes: la ventana de fechas de {@code CrearPlanDiarioUseCase}
 * ({@code FechasPlanificables}), el objetivo semanal del eje en la semana de esa fecha
 * ({@code NO_WEEKLY_ROCK}), las Rocas Maestras completas ({@code ROCKS_LOCKED}) y el cupo por eje y
 * por dia ({@code CupoDelDia}). La posicion no se elige: es la primera libre del eje.
 *
 * <p><b>El dia en curso no se toca</b> ({@code CURRENT_DAY}), aunque la ventana nocturna no haya
 * abierto: si lo que se esta viviendo admite agregados es una decision del dueno todavia pendiente,
 * y hasta entonces vale el criterio de siempre, "lo que se esta viviendo no se reacomoda".
 */
public interface AgregarRocaDiariaUseCase {

    RocaDiaria agregar(AgregarRocaDiariaCommand command);

    /** @param horaInicio hora local o {@code null}; @param horaFin hora local o {@code null} */
    record AgregarRocaDiariaCommand(@NotNull UserId actorId, @NotNull LocalDate fecha, @NotNull EjeObjetivo eje,
                                    String titulo, int puntajeImpacto, boolean esDelegable, LocalTime horaInicio,
                                    LocalTime horaFin) {

        public AgregarRocaDiariaCommand {
            SelfValidating.validateConstructorArgs(AgregarRocaDiariaCommand.class, actorId, fecha, eje, titulo,
                    puntajeImpacto, esDelegable, horaInicio, horaFin);
        }
    }
}
