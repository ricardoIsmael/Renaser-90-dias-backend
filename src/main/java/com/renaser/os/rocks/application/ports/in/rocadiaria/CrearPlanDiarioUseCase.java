package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Planificación nocturna (R-04): crea entre 3 y 9 Rocas Diarias (1-3 por eje). */
public interface CrearPlanDiarioUseCase {

    List<RocaDiaria> crear(CrearPlanDiarioCommand command);

    record CrearPlanDiarioCommand(@NotNull UserId actorId, @NotNull LocalDate fecha,
                                   @NotNull @Size(min = 3, max = 9) List<ItemRocaDiaria> rocas) {

        public CrearPlanDiarioCommand {
            SelfValidating.validateConstructorArgs(CrearPlanDiarioCommand.class, actorId, fecha, rocas);
        }
    }

    /**
     * @param acciones con que se logra ese objetivo del dia: de 0 a 3, en el orden en que se
     *                 escribieron. Vacia es valido — un objetivo puede ser una sola cosa que no
     *                 necesita desglose. Antes estas acciones se escribian el domingo, colgando de
     *                 la semana; desde la V61 viven aca, que es cuando la persona sabe con que
     *                 cuenta.
     */
    record ItemRocaDiaria(EjeObjetivo eje, int posicion, String titulo, String descripcion, int puntajeImpacto,
                           boolean esDelegable, LocalTime horaInicio, LocalTime horaFin,
                           @Size(max = 3) List<String> acciones) {

        public ItemRocaDiaria {
            if (eje == null) {
                throw new IllegalArgumentException("eje es obligatorio en cada roca diaria");
            }
        }
    }
}
