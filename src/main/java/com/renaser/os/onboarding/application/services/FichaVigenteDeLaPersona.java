package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort.FormacionDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada;
import com.renaser.os.onboarding.application.ports.out.ficha.RespuestasDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.TextoParaLaPersona;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lo que comparten quien responde su diagnóstico y quien lo lee (spec 026): la ficha de SU versión, con el plazo y la formación de la persona puestos; sus
 * respuestas por pregunta y en qué estado va.
 */
@Component
class FichaVigenteDeLaPersona {

    static final String SIN_FORMACION = "Renaser";
    static final int DURACION_POR_DEFECTO = 90;

    private final VersionesDeFichaPort versiones;
    private final ConsultarFormacionDeLaPersonaPort formaciones;
    private final LoadRespuestaPort respuestas;
    private final RespuestasDelDiagnosticoPort hayRespuestas;
    private final EtapaOnboardingPort etapas;

    FichaVigenteDeLaPersona(VersionesDeFichaPort versiones, ConsultarFormacionDeLaPersonaPort formaciones, LoadRespuestaPort respuestas,
                            RespuestasDelDiagnosticoPort hayRespuestas, EtapaOnboardingPort etapas) {
        this.versiones = versiones;
        this.formaciones = formaciones;
        this.respuestas = respuestas;
        this.hayRespuestas = hayRespuestas;
        this.etapas = etapas;
    }

    /** La versión de la persona, para su formación y con el plazo y la formación nombrados. */
    FichaDelDiagnostico fichaDe(UserId persona, int version) {
        VersionPublicada publicada = versiones.version(version)
                .orElseThrow(() -> new NoSuchElementException("La versión de tu diagnóstico ya no está disponible"));
        Optional<FormacionDeLaPersona> formacion = formaciones.de(persona);
        Formacion delaPersona = formacion.map(FormacionDeLaPersona::formacion).orElse(null);
        int dias = formacion.map(FormacionDeLaPersona::duracionDias).orElse(DURACION_POR_DEFECTO);
        return publicada.ficha().paraLaFormacion(delaPersona)
                .conTextosPara(TextoParaLaPersona.duracionEnPalabras(dias), formacion.map(FormacionDeLaPersona::nombre).orElse(SIN_FORMACION));
    }

    Map<Integer, Respuesta> respuestasPorPregunta(UserId persona) {
        return respuestas.todasDeUsuario(persona).stream().collect(Collectors.toMap(Respuesta::preguntaId, r -> r, (a, b) -> a));
    }

    String estadoDe(UserId persona) {
        if (terminado(persona)) {
            return "COMPLETADO";
        }
        return hayRespuestas.hayRespuestas(persona) ? "EN_CURSO" : "PENDIENTE";
    }

    boolean terminado(UserId persona) {
        return etapas.flujosCompletados(persona).contains(Diagnostico.FLUJO);
    }
}
