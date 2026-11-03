package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Si los objetivos de ESTA persona pueden haber nacido de un diagnostico (issue #65). Un texto que manda a «confirmar los de tu diagnostico» solo es cierto
 * si el diagnostico existe: a una cuenta de la Escuela sin diagnostico (la ficha estandar) SER le decia que confirmara objetivos que nunca nacieron, cuando
 * los tiene que escribir ella en Plan.
 *
 * <p>Sale del dato real ({@code onboarding}: la ficha de la persona es de diagnostico y lo termino), no de la formacion. Best-effort y del lado seguro: si no
 * se puede leer, {@code false}, y el texto dice solo «escribelos en Plan», que vale para todos y nunca nombra un diagnostico que no se sabe si existe.
 */
final class ObjetivosDelDiagnosticoDeLaPersona {

    private static final Logger log = LoggerFactory.getLogger(ObjetivosDelDiagnosticoDeLaPersona.class);

    private ObjetivosDelDiagnosticoDeLaPersona() {
    }

    static boolean nacieronDeSuDiagnostico(ConsultarDiagnosticoDeLaPersonaPort diagnostico, UserId persona) {
        try {
            DiagnosticoDeLaPersona d = diagnostico.de(persona);
            return d != null && d.asignado() && d.completado();
        } catch (RuntimeException falla) {
            log.info("[rag] los objetivos salen sin mencionar el diagnostico (no se pudo leer): {}", falla.toString());
            return false;
        }
    }
}
