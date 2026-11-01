package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * El diagnóstico de una persona, leído desde otro módulo (spec 026). <b>No autoriza a nadie</b>: quien lo llama (la ficha del mentor, la del líder, la de
 * Administración, SER) ya comprobó que puede ver a esa persona, con sus reglas de siempre y el aislamiento de formaciones (spec 022).
 *
 * <p>Nunca falla por «no lo tiene»: la ficha estándar, o un diagnóstico sin empezar, salen como una lectura sin secciones.
 */
public interface DiagnosticoDeLaPersonaFinder {

    /** Su diagnóstico en palabras, con cada pregunta tal como se le hizo (el texto de SU versión). */
    LecturaDelDiagnostico lecturaDe(UserId persona);

    /** Lo que nace de sus respuestas: los objetivos de 90 días, la prioridad y la primera acción. Vacío si no terminó el diagnóstico. */
    ObjetivosPreparados objetivosDe(UserId persona);

    /**
     * Cómo dirigirse a la persona según lo que contestó en la pregunta del trato del diagnóstico: {@code MASCULINO}, {@code FEMENINO} o {@code NEUTRO}.
     * {@code null} si no tiene diagnóstico o todavía no respondió esa pregunta (quien llama cae a la ficha estándar).
     */
    String tratoDe(UserId persona);

    /**
     * La forma de esta lectura es la misma que la API devuelve tal cual a Administración, al mentor, al líder y a la propia persona (contrato §4).
     *
     * @param ficha  {@code ESTANDAR} o {@code CON_DIAGNOSTICO}
     * @param estado {@code NO_APLICA}, {@code PENDIENTE}, {@code EN_CURSO} o {@code COMPLETADO}
     */
    record LecturaDelDiagnostico(PersonaLeida persona, String ficha, String estado, Integer version, Instant completadoEn,
                                 FormacionLeida formacion, List<SeccionLeida> secciones) {

        public LecturaDelDiagnostico {
            secciones = List.copyOf(secciones);
        }

        public static LecturaDelDiagnostico sinDiagnostico(PersonaLeida persona, String ficha, String estado, Integer version, FormacionLeida formacion) {
            return new LecturaDelDiagnostico(persona, ficha, estado, version, null, formacion, List.of());
        }
    }

    record PersonaLeida(String id, String nombre) {
    }

    record FormacionLeida(String codigo, String nombre) {
    }

    record SeccionLeida(String titulo, List<PreguntaLeida> preguntas) {

        public SeccionLeida {
            preguntas = List.copyOf(preguntas);
        }
    }

    /** {@code respuesta} es {@code null} si todavía no la contestó. {@code retirada}: Administración la quitó después de que la persona empezó. */
    record PreguntaLeida(int id, String texto, String tipo, boolean retirada, RespuestaLeida respuesta) {
    }

    record RespuestaLeida(String texto, List<String> valores, List<FilaLeida> filas) {

        public RespuestaLeida {
            valores = List.copyOf(valores);
            filas = List.copyOf(filas);
        }
    }

    record FilaLeida(String etiqueta, int valor) {
    }

    /** {@code eje}: {@code CUERPO}, {@code TRABAJO} o {@code RELACIONES} (los nombres de {@code rocks.EjeObjetivo}). */
    record ObjetivosPreparados(List<ObjetivoPreparado> objetivos, String prioridad, String primeraAccion) {

        public ObjetivosPreparados {
            objetivos = List.copyOf(objetivos);
        }

        public static ObjetivosPreparados ninguno() {
            return new ObjetivosPreparados(List.of(), null, null);
        }
    }

    record ObjetivoPreparado(String eje, String texto) {
    }
}
