package com.renaser.os.mentoring.application.ports.out;

import com.renaser.os.mentoring.domain.model.nota.NotaDelMentor;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lee las notas privadas del mentor (spec 055, V198). */
public interface LeerNotasDelMentorPort {

    /** Todas las notas del aprendiz, de la más nueva a la más vieja, sin importar quién las escribió. */
    List<NotaDelMentor> delAprendiz(UserId aprendiz);

    Optional<NotaDelMentor> porId(UUID id);
}
