package com.renaser.os.onboarding.application.ports.out.diagnostico;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/** A qué formación pertenece una persona y cuántos días dura su programa: de eso depende qué preguntas ve y cómo se le nombra el plazo. */
public interface ConsultarFormacionDeLaPersonaPort {

    /** Vacío si la persona no pertenece a ninguna formación (Administración, Alquimista, personal sin asignar): ve todas las preguntas y 90 días. */
    Optional<FormacionDeLaPersona> de(UserId persona);

    /** El nombre vigente de una formación (Administración lo edita), para la vista previa. */
    String nombreDe(Formacion formacion);

    record FormacionDeLaPersona(Formacion formacion, String nombre, int duracionDias) {
    }
}
