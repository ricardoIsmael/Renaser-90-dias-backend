package com.renaser.os.rag.infrastructure.adapter.out.onboarding;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.PreguntaLeida;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.RespuestaLeida;
import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Traduce {@code onboarding.api.DiagnosticoDeLaPersonaFinder} al tipo propio de {@code rag} (D-41, spec 026). */
@Component
class ConsultarDiagnosticoDeLaPersonaAdapter implements ConsultarDiagnosticoDeLaPersonaPort {

    private final DiagnosticoDeLaPersonaFinder diagnostico;

    ConsultarDiagnosticoDeLaPersonaAdapter(DiagnosticoDeLaPersonaFinder diagnostico) {
        this.diagnostico = diagnostico;
    }

    @Override
    public DiagnosticoDeLaPersona de(UserId participanteId) {
        LecturaDelDiagnostico lectura = diagnostico.lecturaDe(participanteId);
        if (!"CON_DIAGNOSTICO".equals(lectura.ficha())) {
            return DiagnosticoDeLaPersona.ninguno();
        }
        List<DiagnosticoDeLaPersona.Seccion> secciones = lectura.secciones().stream()
                .map(s -> new DiagnosticoDeLaPersona.Seccion(s.titulo(), s.preguntas().stream().map(this::aPregunta).toList())).toList();
        return new DiagnosticoDeLaPersona(true, "COMPLETADO".equals(lectura.estado()), diagnostico.objetivosDe(participanteId).prioridad(), secciones);
    }

    private DiagnosticoDeLaPersona.Pregunta aPregunta(PreguntaLeida p) {
        return new DiagnosticoDeLaPersona.Pregunta(p.texto(), enPalabras(p.respuesta()));
    }

    private static String enPalabras(RespuestaLeida r) {
        if (r == null) {
            return null;
        }
        if (!r.filas().isEmpty()) {
            return r.filas().stream().map(f -> f.etiqueta() + ": " + f.valor()).collect(Collectors.joining("; "));
        }
        if (!r.valores().isEmpty()) {
            return String.join("; ", r.valores());
        }
        return r.texto();
    }
}
