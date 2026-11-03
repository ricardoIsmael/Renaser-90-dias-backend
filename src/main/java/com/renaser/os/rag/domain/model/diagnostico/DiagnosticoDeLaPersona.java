package com.renaser.os.rag.domain.model.diagnostico;

import java.util.List;

/**
 * El diagnóstico de la persona, visto desde el acompañante (spec 026, 026-8 B: SER lee el diagnóstico COMPLETO). Espejo de
 * {@code onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico}: {@code rag} no expone tipos de otro módulo.
 *
 * <p>Las respuestas son de la persona (ingresos, emociones, situaciones personales): van al contexto de SER y a ningún otro lado, y SER no las repite a
 * nadie más. El texto de cada pregunta es el que se le hizo.
 *
 * @param asignado   le tocó el diagnóstico (en lugar de la ficha estándar)
 * @param completado lo terminó
 * @param prioridad  el eje de su prioridad principal ({@code CUERPO}, {@code TRABAJO}, {@code RELACIONES}), o {@code null}
 */
public record DiagnosticoDeLaPersona(boolean asignado, boolean completado, String prioridad, List<Seccion> secciones) {

    public DiagnosticoDeLaPersona {
        secciones = List.copyOf(secciones);
    }

    public static DiagnosticoDeLaPersona ninguno() {
        return new DiagnosticoDeLaPersona(false, false, null, List.of());
    }

    public record Seccion(String titulo, List<Pregunta> preguntas) {

        public Seccion {
            preguntas = List.copyOf(preguntas);
        }
    }

    /** {@code respuesta} ya viene en palabras (el texto de la opción elegida, las filas de la cuadrícula…), o {@code null} si no la contestó. */
    public record Pregunta(String texto, String respuesta) {
    }
}
