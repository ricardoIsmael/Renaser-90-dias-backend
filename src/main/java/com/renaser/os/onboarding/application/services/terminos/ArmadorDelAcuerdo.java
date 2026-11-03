package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.domain.model.terminos.ProgramaDelAcuerdo;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FechasDelProgramaFinder;
import com.renaser.os.users.api.FechasDelProgramaFinder.FechasDelPrograma;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Llena «Mi aceptación» del Acuerdo de transformación con los datos reales de la persona (spec 039): su nombre completo, su programa y las fechas de inicio y fin. Lo
 * llena el SISTEMA —no una IA ni la persona—, y lo hace en un solo lugar para que lo que se muestra al leer y lo que se guarda al firmar sean lo mismo.
 *
 * <p>Las fechas no se calculan aquí: salen de {@link FechasDelProgramaFinder}, que usa la regla de la spec 036 (mañana en la zona de la persona) y la cuenta del último
 * día del programa. Quien no tiene un programa que mostrar (el personal que no activó el suyo) lleva su nombre, el nombre del programa de su formación y ninguna fecha:
 * no se le promete un inicio que no eligió.
 */
@Component
class ArmadorDelAcuerdo {

    private final ConsultarActorPort actores;
    private final FechasDelProgramaFinder programas;

    ArmadorDelAcuerdo(ConsultarActorPort actores, FechasDelProgramaFinder programas) {
        this.actores = actores;
        this.programas = programas;
    }

    /** Sin cronograma de pagos: no va en los términos (D-315). */
    AcuerdoDeLaPersona armar(UserId persona, Formacion formacion) {
        String nombre = actores.deActor(persona).map(ActorOnboarding::nombre).map(String::trim).orElse("");
        Optional<FechasDelPrograma> fechas = programas.de(persona).filter(f -> f.inicio() != null);
        Integer duracion = fechas.map(FechasDelPrograma::duracionDias).orElse(null);
        return new AcuerdoDeLaPersona(nombre, ProgramaDelAcuerdo.etiqueta(formacion, duracion), fechas.map(FechasDelPrograma::inicio).orElse(null),
                fechas.map(FechasDelPrograma::fin).orElse(null), fechas.map(FechasDelPrograma::activado).orElse(false));
    }
}
