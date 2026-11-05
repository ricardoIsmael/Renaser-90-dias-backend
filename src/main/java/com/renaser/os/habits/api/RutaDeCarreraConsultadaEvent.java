package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alguien abrió la ruta de una carrera de OTRA persona (spec 042, FR-041, decisión del dueño: «cada lectura ajena queda
 * registrada»). Lo publica {@code habits} en la misma transacción que la lectura, así que el outbox de Modulith lo reentrega si el
 * oyente falla; {@code leadership} lo anota en {@code consultas_de_ficha} con {@code tipo = RUTA_DE_CARRERA}. La propia persona
 * mirando su ruta no publica nada.
 *
 * @param consultaId   determinístico por lectura: es el id de la fila, y una reentrega no la duplica
 * @param grupoId      el grupo desde el que se la miró, o {@code null}
 * @param mentorId     el mentor vigente de ese grupo, o {@code null}
 * @param consultadaEn el instante de la lectura, no el de la entrega del evento
 */
public record RutaDeCarreraConsultadaEvent(UUID consultaId, UserId consultorId, UserId personaId, UUID grupoId, UserId mentorId,
                                           Instant consultadaEn) {

    public RutaDeCarreraConsultadaEvent {
        Objects.requireNonNull(consultaId, "consultaId");
        Objects.requireNonNull(consultorId, "consultorId");
        Objects.requireNonNull(personaId, "personaId");
        Objects.requireNonNull(consultadaEn, "consultadaEn");
    }
}
