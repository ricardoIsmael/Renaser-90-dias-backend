package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Set;

/**
 * Los aprendices que una persona acompaña HOY: la pregunta de lo que un mentor o una guía puede ver de «los suyos» (p. ej. los tickets
 * de mentoría).
 *
 * <p>Interfaz aparte de {@link AcompanamientoFinder} por el mismo motivo que {@link MentorVigenteFinder}: aquella tiene dobles de prueba
 * escritos a mano en otros módulos, y sumarle un método abstracto obligaba a tocarlos todos.
 *
 * <p>«Acompaña» es exactamente lo que ya responde {@link AcompanamientoFinder#acompanaVigente} para {@code /mentor/groups/{id}/learners}:
 * una asignación vigente que no sea de aprendiz (mentor, guía o soporte), en un grupo que está corriendo en ese instante y de la
 * formación que la persona tiene activa. Quien acompañó antes, o un grupo que ya cerró, no cuenta.
 */
public interface AprendicesDelAcompananteFinder {

    /**
     * Los aprendices con pertenencia vigente a los grupos que {@code acompanante} acompaña en ese instante. Vacío si no acompaña ninguno
     * (también si es solo aprendiz de un grupo). Una consulta por cada grupo suyo, no una por aprendiz.
     */
    Set<UserId> aprendicesQueAcompana(UserId acompanante, Instant instante);
}
