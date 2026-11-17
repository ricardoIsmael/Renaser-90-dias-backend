package com.renaser.os.evidence.application.ports.out.acompanamiento;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Si un mentor o una guía acompaña HOY a un aprendiz. La respuesta es la de {@code community} (la asignación vigente del
 * grupo, la misma que usa la ficha del mentor), no una regla de este módulo: {@code participantes_programa.mentor_id}
 * puede estar vacío o ser de un mentor anterior y no se usa para autorizar (issue #167).
 */
public interface AcompananteDelAprendizPort {

    boolean acompanaA(UserId acompanante, UserId aprendiz, Instant instante);
}
