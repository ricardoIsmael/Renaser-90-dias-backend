package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El diagnóstico de una persona, leído desde otro módulo (spec 026). <b>No autoriza a nadie</b>: quien lo llama (la ficha del mentor, la del líder, la de
 * Administración, SER) ya comprobó que puede ver a esa persona, con sus reglas de siempre y el aislamiento de formaciones (spec 022).
 *
 * <p>Nunca falla por «no lo tiene»: la ficha estándar, o un diagnóstico sin empezar, salen como una lectura sin secciones.
 */
public interface DiagnosticoDeLaPersonaFinder {

    /** Su diagnóstico en palabras, con cada pregunta tal como se le hizo (el texto de SU versión). No deja constancia: es la lectura de la propia persona y de SER. */
    LecturaDelDiagnostico lecturaDe(UserId persona);

    /**
     * Lo mismo cuando OTRA persona lo abre (el mentor o la guía, el líder, Administración o Alquimista): además deja constancia de quién lo vio y cuándo
     * ({@link DiagnosticoConsultadoEvent}). Hay que llamarlo dentro de una transacción de escritura, porque el evento se publica en ella; solo deja
     * constancia si la lectura trae un diagnóstico con preguntas (un «no tiene» no enseña nada).
     */
    LecturaDelDiagnostico lecturaDe(UserId persona, LectorDelDiagnostico lector);

    /**
     * Quién lo abre y desde dónde.
     *
     * @param grupoId  el grupo desde el que se la miró; {@code null} si no pasó por un grupo
     * @param mentorId el mentor vigente de ese grupo; {@code null} si no tenía
     */
    record LectorDelDiagnostico(UserId consultorId, UUID grupoId, UserId mentorId) {

        public LectorDelDiagnostico {
            Objects.requireNonNull(consultorId, "consultorId es obligatorio");
        }
    }

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

    /** {@code meta} es {@code null} cuando no respondió la parte medible (el objetivo vale en palabras). */
    record ObjetivoPreparado(String eje, String texto, MetaPreparada meta) {

        public ObjetivoPreparado(String eje, String texto) {
            this(eje, texto, null);
        }
    }

    /** A dónde quiere llegar, desde dónde parte y en qué unidad (spec 026). En Relaciones la unidad es la escala «/10». */
    record MetaPreparada(BigDecimal meta, BigDecimal partida, String unidad) {
    }
}
