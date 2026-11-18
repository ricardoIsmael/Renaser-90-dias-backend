package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El color y el porcentaje de cada día de una persona, para que el líder pinte la semana y el mes de su ficha (spec 056, FR-017 y
 * FR-018). Es la misma lectura que hace el mentor de su aprendiz ({@code ConsultarDiasDelAprendizUseCase}, spec 055): cambia la puerta
 * —el líder lo ve por su formación, esté o no la persona en un grupo— y no la regla del semáforo.
 */
public interface ConsultarDiasDeLaPersonaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code RANGO_INVALIDO} (400) si el rango va al revés o pasa de
     *                                                                 {@code RangoDeDias.DIAS_MAXIMOS} días; {@code SIN_PROGRAMA} (404) si es un
     *                                                                 mentor o una guía que no sigue su programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    si quien pide no es del liderazgo o su cuenta está suspendida
     * @throws java.util.NoSuchElementException                       (404) si la persona no existe, no se muestra o es de otra formación
     */
    DiasDeLaPersona diasDe(ConsultaDiasDeLaPersona consulta);

    record ConsultaDiasDeLaPersona(UserId actorId, UUID personaId, LocalDate desde, LocalDate hasta) {

        public ConsultaDiasDeLaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(desde, "desde es obligatorio");
            Objects.requireNonNull(hasta, "hasta es obligatorio");
        }
    }

    /**
     * @param zona   la zona de la persona: sus días son los suyos, no los del servidor ni los del líder
     * @param diaUno el Día 1 de su programa; null si todavía no lo eligió (entonces no hay días)
     * @param dias   del más viejo al más nuevo, recortados a [Día 1, hoy]; sin los de fuera del programa
     */
    record DiasDeLaPersona(String zona, LocalDate diaUno, List<DiaDelSemaforo> dias) {

        public DiasDeLaPersona {
            dias = dias == null ? List.of() : List.copyOf(dias);
        }
    }
}
