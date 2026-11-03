package com.renaser.os.onboarding.application.ports.in.diagnostico;

import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.RespuestaEntrante;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** El diagnóstico de la propia persona (spec 026, contrato §1): abrirlo, responderlo por pasos y terminarlo. */
public interface DiagnosticoDeLaPersonaUseCase {

    EstadoDeMiDiagnostico estado(UserId persona);

    /** La primera vez fija la versión publicada vigente como la suya. 404 sin diagnóstico asignado o publicado; 409 si ya lo terminó. */
    DiagnosticoAbierto abrir(UserId persona);

    /** Guarda un paso entero. Devuelve cuántas respuestas quedaron guardadas (las vacías borran la anterior). */
    int guardar(UserId persona, List<RespuestaEntrante> respuestas);

    ResultadoDeTerminar terminar(UserId persona);

    ResumenDeMiDiagnostico resumen(UserId persona);

    /** {@code estado}: NO_APLICA, PENDIENTE, EN_CURSO o COMPLETADO. */
    record EstadoDeMiDiagnostico(TipoDeFicha ficha, String estado, Integer version, Instant completadoEn) {
    }

    /** Todo lo que necesita abrir el recorrido: los textos ya llevan el plazo y la formación de la persona. */
    record DiagnosticoAbierto(int version, String estado, String titulo, String tituloDeLaPortada, String mensajeDeLaPortada,
                              String formacionCodigo, String formacionNombre, int duracionDias, String duracionTexto, Integer paso,
                              FichaDelDiagnostico ficha, Map<Integer, Respuesta> respuestas, Map<Integer, String> precargas) {
    }

    record ObjetivoNacido(String eje, String texto) {
    }

    record ResumenDeMiDiagnostico(boolean completado, String prioridad, String primeraAccion, List<ObjetivoNacido> objetivos) {

        public ResumenDeMiDiagnostico {
            objetivos = List.copyOf(objetivos);
        }

        public static ResumenDeMiDiagnostico vacio() {
            return new ResumenDeMiDiagnostico(false, null, null, List.of());
        }
    }

    record ResultadoDeTerminar(String estado, List<ObjetivoNacido> objetivos, String prioridad, String primeraAccion) {

        public ResultadoDeTerminar {
            objetivos = List.copyOf(objetivos);
        }
    }
}
