package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona.Pregunta;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona.Seccion;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 026, 026-8 B: {@code consultar_mi_diagnostico} le da a SER el diagnóstico completo con las palabras de la persona. */
class ConsultarMiDiagnosticoHerramientaTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());

    private static ResultadoHerramienta ejecutar(ConsultarDiagnosticoDeLaPersonaPort puerto) {
        return new ConsultarMiDiagnosticoHerramienta(puerto)
                .ejecutar(PERSONA, InvocacionHerramienta.sinArgumentos(ConsultarMiDiagnosticoHerramienta.NOMBRE));
    }

    private static String contenido(ConsultarDiagnosticoDeLaPersonaPort puerto) {
        ResultadoHerramienta resultado = ejecutar(puerto);
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    @Test
    @DisplayName("devuelve todas las secciones y preguntas con sus respuestas, la prioridad y si está completo")
    void completo() {
        DiagnosticoDeLaPersona d = new DiagnosticoDeLaPersona(true, true, "CUERPO", List.of(
                new Seccion("Relaciones", List.of(new Pregunta("¿Cómo está tu pareja?", "Distante, hablamos poco"))),
                new Seccion("Tus objetivos", List.of(new Pregunta("¿Qué quieres lograr en tu cuerpo?", "Bajar de 92 a 85 kg\ny dormir mejor"),
                        new Pregunta("¿Y si no actúas?", null)))));

        String texto = contenido(persona -> d);

        assertThat(texto).contains("(completo)").contains("Prioridad principal: Salud y bienestar")
                .contains("Relaciones:").contains("¿Cómo está tu pareja? -> Distante, hablamos poco")
                .contains("Bajar de 92 a 85 kg y dormir mejor").contains("¿Y si no actúas? -> (sin responder)");
    }

    @Test
    @DisplayName("sin diagnóstico dice que hizo la ficha estándar y que no invente; empezado sin respuestas, que todavía no empieza")
    void sinDiagnosticoOSinEmpezar() {
        assertThat(contenido(persona -> DiagnosticoDeLaPersona.ninguno())).contains("no tiene diagnostico").contains("No inventes");
        assertThat(contenido(persona -> new DiagnosticoDeLaPersona(true, false, null, List.of()))).contains("todavia no empezo");
    }

    @Test
    @DisplayName("un diagnóstico a medias se marca incompleto")
    void incompleto() {
        DiagnosticoDeLaPersona d = new DiagnosticoDeLaPersona(true, false, null, List.of(
                new Seccion("Sobre ti", List.of(new Pregunta("¿Cómo te llamas?", "María")))));

        assertThat(contenido(persona -> d)).contains("incompleto").doesNotContain("Prioridad principal");
    }

    @Test
    @DisplayName("si no se puede leer, la herramienta falla con un mensaje y no rompe la conversación")
    void fallaLaLectura() {
        ResultadoHerramienta resultado = ejecutar(persona -> {
            throw new IllegalStateException("base caída");
        });

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("No pude leer");
    }

    @Test
    @DisplayName("es de solo lectura, sin parámetros y con su nombre estable")
    void definicion() {
        var definicion = new ConsultarMiDiagnosticoHerramienta(persona -> DiagnosticoDeLaPersona.ninguno()).definicion();

        assertThat(definicion.nombre()).isEqualTo("consultar_mi_diagnostico");
    }
}
