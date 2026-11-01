package com.renaser.os.onboarding.infrastructure.adapter.out.cuentas;

import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** La formación de la persona y la duración de su programa, de las {@code api} de {@code users} (spec 022). */
@Component
class FormacionDeLaPersonaAdapter implements ConsultarFormacionDeLaPersonaPort {

    private final AlcanceDeFormacionFinder alcance;
    private final FormacionesFinder formaciones;
    private final ParticipacionProgramaFinder participaciones;

    FormacionDeLaPersonaAdapter(AlcanceDeFormacionFinder alcance, FormacionesFinder formaciones, ParticipacionProgramaFinder participaciones) {
        this.alcance = alcance;
        this.formaciones = formaciones;
        this.participaciones = participaciones;
    }

    @Override
    public Optional<FormacionDeLaPersona> de(UserId persona) {
        Optional<Formacion> formacion = alcance.formacionDe(persona);
        if (formacion.isEmpty()) {
            return Optional.empty();
        }
        int duracion = participaciones.deParticipante(persona).filter(p -> p.inscrito()).map(p -> p.duracionDias())
                .orElse(formaciones.de(formacion.get()).duracionPorDefecto());
        return Optional.of(new FormacionDeLaPersona(formacion.get(), formaciones.de(formacion.get()).nombre(), duracion));
    }

    @Override
    public String nombreDe(Formacion formacion) {
        return formaciones.de(formacion).nombre();
    }
}
