package com.renaser.os.onboarding.application.ports.in.diagnostico;

import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaAdministrada;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.DiagnosticoAbierto;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort.VersionPublicada;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Ver el borrador, la vista previa, publicar, las versiones, la plantilla y la lectura del diagnóstico de una persona (solo Administración y Alquimista). */
public interface PublicarDiagnosticoUseCase {

    BorradorAdministrado ver(UserId actor);

    /** El borrador como lo vería una persona de esa formación y duración; {@code formacion == null}: todas las preguntas, 90 días. */
    DiagnosticoAbierto vistaPrevia(UserId actor, Formacion formacion, Integer duracionDias);

    /** Publica el borrador como la versión siguiente. 409 si alguien publicó después de {@code versionBase}; 400 si no se puede publicar. */
    VersionPublicada publicar(UserId actor, int versionBase);

    List<VersionPublicada> versiones(UserId actor);

    /** La plantilla de importación vacía, en CSV. */
    String plantilla(UserId actor);

    /** El borrador en el formato de la plantilla, para editarlo fuera y volver a importarlo. */
    String exportar(UserId actor);

    LecturaDelDiagnostico lecturaDe(UserId actor, UserId persona);

    record BorradorAdministrado(VersionPublicada publicada, boolean hayCambiosSinPublicar, List<String> motivosParaNoPublicar,
                                List<SeccionAdministrada> secciones, List<PreguntaAdministrada> retiradas) {

        public BorradorAdministrado {
            motivosParaNoPublicar = List.copyOf(motivosParaNoPublicar);
            secciones = List.copyOf(secciones);
            retiradas = List.copyOf(retiradas);
        }

        public int totalPreguntas() {
            return secciones.stream().mapToInt(s -> s.preguntas().size()).sum();
        }
    }

    record SeccionAdministrada(SeccionDelDiagnostico seccion, List<PreguntaAdministrada> preguntas) {

        public SeccionAdministrada {
            preguntas = List.copyOf(preguntas);
        }
    }
}
