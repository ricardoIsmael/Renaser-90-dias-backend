package com.renaser.os.mentoring.application.ports.out;

import com.renaser.os.mentoring.domain.model.nota.NotaDelMentor;
import com.renaser.os.mentoring.domain.model.nota.VersionDeLaNota;

/** Escribe las notas privadas del mentor (spec 055, V198). No hay borrado: una corrección guarda su versión anterior. */
public interface GuardarNotaDelMentorPort {

    void insertar(NotaDelMentor nota);

    /** Deja la nota con su texto nuevo y guarda, en la misma transacción de quien llama, el texto que reemplazó. */
    void corregir(NotaDelMentor nota, VersionDeLaNota anterior);
}
