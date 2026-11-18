package com.renaser.os.leadership.application.services;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionesFinder;
import org.springframework.stereotype.Component;

/**
 * La formación de una persona y lo que ve quien pide, para los documentos (spec 056): el nombre que Administración le puso a la formación —el
 * documento nunca lo escribe a mano— y el alcance de formaciones del líder. Todo por las APIs de {@code users}, el único lugar que lo decide.
 */
@Component
class FormacionDeLaPersona {

    private final AlcanceDeFormacionFinder alcance;
    private final FormacionesFinder formaciones;

    FormacionDeLaPersona(AlcanceDeFormacionFinder alcance, FormacionesFinder formaciones) {
        this.alcance = alcance;
        this.formaciones = formaciones;
    }

    AlcanceDeFormacion alcanceDe(UserId actor) {
        return alcance.de(actor);
    }

    /** El nombre de su formación; null si no pertenece a ninguna (Administración sin programa). */
    String nombreDe(UserId persona) {
        return alcance.formacionDe(persona).map(formacion -> formaciones.de(formacion).nombre()).orElse(null);
    }
}
