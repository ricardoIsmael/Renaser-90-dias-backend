package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public interface ActualizarCelulaUseCase {

    /** Proyeccion de respuesta dentro de la misma transaccion (CLAUDE.MD sec. 5.4.6). */
    CelulaDetalle actualizar(ActualizarCelulaCommand command);

    /**
     * {@code tocaUrlVideollamada} distingue "no vino" de "vino null para borrarla"
     * (community/schema.ts:48-51).
     *
     * <p>{@code tocaPeriodo} (V48) hace lo mismo con las dos fechas, y aca la distincion no es
     * cosmetica: sin ella, un PATCH que solo renombra el grupo le borraria el periodo y el grupo
     * dejaria de cerrarse sin que nadie lo pidiera. Con {@code tocaPeriodo} en true y las dos
     * fechas en null, el periodo se borra a proposito.
     */
    record ActualizarCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId, String nombre,
                                    String urlVideollamada, boolean tocaUrlVideollamada, LocalDate periodoInicio,
                                    LocalDate periodoFin, boolean tocaPeriodo, Integer capacidad,
                                    boolean tocaCapacidad) {

        public ActualizarCelulaCommand {
            // El ORDEN importa: `validateConstructorArgs` empareja estos valores con los
            // componentes del record por POSICION. Los tres nuevos van AL FINAL justamente por eso
            // — meterlos en el medio corre todos los de atras y las anotaciones terminan validando
            // el campo equivocado.
            SelfValidating.validateConstructorArgs(ActualizarCelulaCommand.class, actorId, celulaId, nombre,
                    urlVideollamada, tocaUrlVideollamada, periodoInicio, periodoFin, tocaPeriodo, capacidad,
                    tocaCapacidad);
            // Nivel 2 (CLAUDE.MD sec. 5.4.3): un periodo a medias o al reves no llega a construir
            // el comando. La regla es UNA sola y vive en el agregado, no duplicada aca.
            Celula.periodoDe(periodoInicio, periodoFin);
        }

        /** Sobrecarga previa a V48: no toca el periodo. Se conserva para no obligar a tocar a los
         * llamadores que no lo mandan, mismo criterio que las factorias de {@link Celula}. */
        public ActualizarCelulaCommand(UserId actorId, CelulaId celulaId, String nombre, String urlVideollamada,
                                        boolean tocaUrlVideollamada) {
            this(actorId, celulaId, nombre, urlVideollamada, tocaUrlVideollamada, null, null, false, null, false);
        }

        /** Sobrecarga previa al SDD 003: no toca la capacidad. */
        public ActualizarCelulaCommand(UserId actorId, CelulaId celulaId, String nombre, String urlVideollamada,
                                        boolean tocaUrlVideollamada, LocalDate periodoInicio, LocalDate periodoFin,
                                        boolean tocaPeriodo) {
            this(actorId, celulaId, nombre, urlVideollamada, tocaUrlVideollamada, periodoInicio, periodoFin,
                    tocaPeriodo, null, false);
        }

        /** El periodo ya armado, o {@code null} — que con {@code tocaPeriodo} en true significa
         * "borralo", y con false no se mira. */
        public PeriodoGrupo periodo() {
            return Celula.periodoDe(periodoInicio, periodoFin);
        }
    }
}
