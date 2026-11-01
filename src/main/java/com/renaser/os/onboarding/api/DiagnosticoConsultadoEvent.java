package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alguien abrió el diagnóstico de otra persona (spec 026, decisión del dueño: «queda constancia de quién lo vio y cuándo»). Lo publica
 * {@code onboarding} en la misma transacción que la lectura —el mentor o la guía de su grupo, el líder, Administración o Alquimista—, así que el
 * outbox de Modulith lo reentrega si el oyente falla. {@code leadership} lo anota en {@code consultas_de_ficha}.
 *
 * <p>La propia persona leyendo su diagnóstico no publica nada.
 *
 * @param consultaId   determinístico por lectura: es el id de la fila, y una reentrega no la duplica
 * @param consultorId  quién lo abrió
 * @param personaId    de quién es el diagnóstico
 * @param grupoId      el grupo desde el que se la miró; {@code null} si no pasó por un grupo (Administración, o el líder sin grupo)
 * @param mentorId     el mentor vigente de ese grupo, o {@code null}
 * @param consultadaEn el instante de la lectura, no el de la entrega del evento
 */
public record DiagnosticoConsultadoEvent(UUID consultaId, UserId consultorId, UserId personaId, UUID grupoId, UserId mentorId,
                                         Instant consultadaEn) {

    public DiagnosticoConsultadoEvent {
        Objects.requireNonNull(consultaId, "consultaId");
        Objects.requireNonNull(consultorId, "consultorId");
        Objects.requireNonNull(personaId, "personaId");
        Objects.requireNonNull(consultadaEn, "consultadaEn");
    }
}
