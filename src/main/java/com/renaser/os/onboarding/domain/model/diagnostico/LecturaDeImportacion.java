package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;
import java.util.Set;

/** Lo que se entendió de un archivo de importación: las preguntas, y los errores y avisos por número de fila (la cabecera es la fila 1). */
public record LecturaDeImportacion(List<PreguntaImportada> preguntas, List<ErrorDeImportacion> errores,
                                   List<AvisoDeImportacion> avisos) {

    public LecturaDeImportacion {
        preguntas = List.copyOf(preguntas);
        errores = List.copyOf(errores);
        avisos = List.copyOf(avisos);
    }

    public boolean tieneErrores() {
        return !errores.isEmpty();
    }

    /** Los títulos de las secciones, en el orden en que aparecen. */
    public List<String> secciones() {
        return preguntas.stream().map(PreguntaImportada::seccion).distinct().toList();
    }

    /** Una pregunta del archivo ya validada con las reglas del dominio. {@code condicionClave} se resuelve a un id al aplicarla. */
    public record PreguntaImportada(int fila, String seccion, String clave, PreguntaDelDiagnostico pregunta,
                                    String condicionClave, String condicionValor) {

        public TipoPreguntaOnboarding tipo() {
            return pregunta.tipo();
        }

        public Set<Formacion> formaciones() {
            return pregunta.formaciones();
        }
    }

    /** {@code columna} puede ser {@code null} cuando el error es de toda la fila. */
    public record ErrorDeImportacion(int fila, String columna, String mensaje) {
    }

    public record AvisoDeImportacion(int fila, String mensaje) {
    }
}
