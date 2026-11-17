package com.renaser.os.mentoring.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Las notas privadas que se escribieron sobre un aprendiz (spec 055), de la más nueva a la más vieja, para quien solo las LEE: el
 * liderazgo de su formación ({@code leadership}). Todas con {@code puedeCorregir = false}.
 *
 * <p>No autoriza: quien llama ya estableció que el actor puede ver a esa persona.
 */
public interface NotasDelMentorFinder {

    List<NotaDelAprendiz> notasDe(UserId aprendiz);
}
