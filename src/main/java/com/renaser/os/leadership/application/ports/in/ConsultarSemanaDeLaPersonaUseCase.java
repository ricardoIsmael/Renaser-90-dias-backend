package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase.SemanaDelAprendizParaElMentor;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La semana de una persona de sábado a viernes, día por día y con el pilar de cada hábito, para la ficha del LÍDER (spec 056, FR-014 y
 * FR-021). Es la misma lectura y el mismo cuerpo que la del mentor de su aprendiz
 * ({@link ConsultarSemanaDelAprendizDelMentorUseCase}, spec 055): cambia la puerta —el líder la ve por su formación, esté o no la
 * persona en un grupo, sea aprendiz o mentor con su programa— y que <b>sí deja la constancia de consulta</b> del líder, como la ficha
 * por semana de siempre ({@link ConsultarSemanaDePersonaUseCase}, spec 005 FR-016).
 *
 * <p>No confundir con {@link ConsultarSemanaDePersonaUseCase}: aquella es la ventana de los 7 días que terminan ayer, sin la semana en
 * curso ni el Día 1; ésta es la semana del semáforo.
 */
public interface ConsultarSemanaDeLaPersonaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA} (400);
     *                                                                 {@code SIN_PROGRAMA} (404) si es un mentor o una guía que no sigue su programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    si quien pide no es del liderazgo o su cuenta está suspendida
     * @throws java.util.NoSuchElementException                       (404) si la persona no existe, no se muestra o es de otra formación
     */
    SemanaDelAprendizParaElMentor semanaDe(ConsultaSemanaDeLaPersona consulta);

    /** @param semanaHasta el viernes que cierra la semana; null = la semana en curso de la persona */
    record ConsultaSemanaDeLaPersona(UserId actorId, UUID personaId, LocalDate semanaHasta) {

        public ConsultaSemanaDeLaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
