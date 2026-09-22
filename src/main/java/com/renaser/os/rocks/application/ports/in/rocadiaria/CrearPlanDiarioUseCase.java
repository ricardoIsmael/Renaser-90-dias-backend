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

/**
 * Planificacion nocturna (R-04): crea entre 1 y 9 objetivos diarios (1-3 por eje).
 *
 * > <b>Corregido el 2026-09-22 (E-206).</b> El minimo era <b>3</b>, y venia de asumir que siempre
 * > habia tres ejes planificados: 1 por eje, tres ejes. Desde que la semana se puede armar con un
 * > solo eje (RK-12) esa cuenta dejo de cerrar — con un eje solo se puede agendar de ese eje, o
 * > sea 1 a 3, y agendar una sola accion volvia con
 * > <i>"CrearPlanDiarioCommand.rocas: el tamano debe estar entre 3 y 9"</i>. La persona armaba su
 * > semana como la app le decia que podia, y despues no podia planificar el dia.
 *
 * <p><b>Esto NO es el bloqueo de las 20:00.</b> {@code BloqueoPlanificacion.ROCAS_REQUERIDAS_MANANA}
 * sigue en 3 por decision expresa del dueno, y son dos cosas distintas: aquello decide si la app
 * insiste cuando manana esta flojo, esto decide si se puede guardar. Un minimo de creacion de 3
 * no hace que nadie planifique mejor — hace que no pueda planificar.
 */
public interface CrearPlanDiarioUseCase {

    List<RocaDiaria> crear(CrearPlanDiarioCommand command);

    record CrearPlanDiarioCommand(@NotNull UserId actorId, @NotNull LocalDate fecha,
                                   @NotNull @Size(min = 1, max = 9) List<ItemRocaDiaria> rocas) {

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
