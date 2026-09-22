package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Planning Semanal (W-02): crea los objetivos semanales de la semana entrante, uno por eje.
 *
 * <p><b>Alcanza con UNO.</b> Hasta el 2026-09-22 exigia los tres, y el efecto era que quien no
 * tenia claros los otros dos no podia guardar ninguno: doce campos minimos en una sentada, o nada.
 * El dueno lo corrigio con el criterio del Mapa — manda el eje que la persona eligio como
 * principal, y los otros dos se suman cuando quiera.
 *
 * <p>El maximo sigue en tres: hay un objetivo semanal por eje y no existe un cuarto eje.
 */
public interface CrearPlanSemanalUseCase {

    List<RocaSemanal> crear(CrearPlanSemanalCommand command);

    record CrearPlanSemanalCommand(@NotNull UserId actorId,
                                    @NotNull @Size(min = 1, max = 3) List<@Valid ItemRocaSemanal> rocas) {

        public CrearPlanSemanalCommand {
            SelfValidating.validateConstructorArgs(CrearPlanSemanalCommand.class, actorId, rocas);
        }
    }

    /**
     * @param accionCritica1 las tres son <b>opcionales</b> desde el 2026-09-22: las acciones pasaron
     *                       al objetivo diario ({@code AccionDiaria}, V61) y la semana quedo en su
     *                       objetivo. Se siguen aceptando para no romper a quien las mande.
     */
    record ItemRocaSemanal(EjeObjetivo eje, String titulo, String accionCritica1, String accionCritica2,
                            String accionCritica3, String obstaculo, String contingencia,
                            Integer autoevaluacionInicio) {

        public ItemRocaSemanal {
            if (eje == null) {
                throw new IllegalArgumentException("eje es obligatorio en cada roca semanal");
            }
        }
    }
}
