package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public interface CrearCelulaUseCase {

    /** Devuelve la proyeccion que la API responde — armada dentro de la MISMA
     * transaccion que crea la celula (CLAUDE.MD sec. 5.4.6). */
    CelulaDetalle crear(CrearCelulaCommand command);

    /**
     * {@code periodoInicio}/{@code periodoFin} (V48): las dos o ninguna. Ninguna = grupo sin
     * periodo, que es lo que fueron todas las celulas hasta V48 y sigue siendo valido.
     *
     * <p>{@code tipo} y {@code capacidad} entran con el SDD 003. Antes no se podian elegir al
     * crear —el alta forzaba REGULAR con la capacidad de la politica— y eso dejaba el grupo de
     * bienvenida de siete dias fuera del alcance del administrador: tenia que existir un
     * RECEPCION y no habia forma de crearlo desde la API. {@code capacidad} null = la de la
     * politica de la cohorte; en un RECEPCION se ignora, que no tiene tope (D-05).
     */
    record CrearCelulaCommand(@NotNull UserId actorId, @NotBlank String nombre, @NotNull CohorteId cohorteId,
                               String urlVideollamada, LocalDate periodoInicio, LocalDate periodoFin,
                               TipoCelula tipo, Integer capacidad) {

        public CrearCelulaCommand {
            // El ORDEN importa: `validateConstructorArgs` empareja estos valores con los
            // componentes del record por POSICION. Los dos nuevos van AL FINAL justamente por eso
            // — meterlos en el medio corre todos los de atras y las anotaciones terminan validando
            // el campo equivocado.
            SelfValidating.validateConstructorArgs(CrearCelulaCommand.class, actorId, nombre, cohorteId,
                    urlVideollamada, periodoInicio, periodoFin, tipo, capacidad);
            // Nivel 2 (CLAUDE.MD sec. 5.4.3): un periodo a medias o al reves no llega a construir
            // el comando. La regla es UNA sola y vive en el agregado, no duplicada aca.
            Celula.periodoDe(periodoInicio, periodoFin);
        }

        /** Sobrecarga previa a V48: alta sin periodo. Se conserva para no obligar a tocar a los
         * llamadores que no lo mandan, mismo criterio que las factorias de {@link Celula}. */
        public CrearCelulaCommand(UserId actorId, String nombre, CohorteId cohorteId, String urlVideollamada) {
            this(actorId, nombre, cohorteId, urlVideollamada, null, null, null, null);
        }

        /** Sobrecarga previa al SDD 003: alta con periodo pero sin tipo ni capacidad explicitos. */
        public CrearCelulaCommand(UserId actorId, String nombre, CohorteId cohorteId, String urlVideollamada,
                                   LocalDate periodoInicio, LocalDate periodoFin) {
            this(actorId, nombre, cohorteId, urlVideollamada, periodoInicio, periodoFin, null, null);
        }

        /** {@code null} = REGULAR, que es lo que era todo grupo antes de que el tipo fuera elegible. */
        public TipoCelula tipoEfectivo() {
            return tipo != null ? tipo : TipoCelula.REGULAR;
        }

        /** El periodo ya armado, o {@code null} si el grupo no tiene. */
        public PeriodoGrupo periodo() {
            return Celula.periodoDe(periodoInicio, periodoFin);
        }
    }
}
