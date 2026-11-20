package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Los objetivos de una persona, solo para leer, en la ficha del líder (spec 056, FR-027): los de los 90 días, el del mes en curso y
 * los de la semana. Salen de {@code rocks.api.RocasDelAprendizFinder}, el mismo contrato con el que el acompañante lee las rocas de un
 * aprendiz: no hay una regla nueva ni un cálculo propio, y nada se puede escribir desde acá.
 */
public interface ConsultarObjetivosDeLaPersonaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SIN_PROGRAMA} (404) si es un mentor o una guía que no sigue su programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    si quien pide no es del liderazgo o su cuenta está suspendida
     * @throws java.util.NoSuchElementException                       (404) si la persona no existe, no se muestra o es de otra formación
     */
    ObjetivosDelAprendiz objetivosDe(ConsultaObjetivosDeLaPersona consulta);

    record ConsultaObjetivosDeLaPersona(UserId actorId, UUID personaId) {

        public ConsultaObjetivosDeLaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
