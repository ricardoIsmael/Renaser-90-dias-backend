package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El prompt de sistema del ACOMPANANTE (D-102) es un archivo de texto que solo se parsea cuando
 * alguien construye {@code GoogleGenAiRenasiaChatAdapter} — o sea, solo con
 * {@code renaser.ia.proveedor=google} y credenciales reales. Un error de sintaxis de StringTemplate
 * (una llave suelta en la prosa, por ejemplo) no rompia ninguna prueba: aparecia en produccion, el
 * primer dia con credenciales. Esta prueba cierra ese hueco — renderiza el archivo real, sin
 * mockear nada. El prompt de Sparkie tiene la suya: {@link PromptSparkieCursosTest}.
 */
class PromptSistemaRenasiaTest {

    private static final String RECURSO = GoogleGenAiRenasiaChatAdapter.RECURSO_PROMPT_ACOMPANANTE;

    /** Solo {@code contexto}: si el archivo volviera a tener {@code {ambito}}, el render fallaria aca. */
    private static String renderizar(String contexto) {
        return new PromptTemplate(new ClassPathResource(RECURSO)).render(Map.of("contexto", contexto));
    }

    @Test
    @DisplayName("el prompt real parsea y sustituye el contexto recuperado")
    void renderizaElArchivoRealConSuContexto() {
        String render = renderizar("- La leccion 3 habla del ritual de manana.");

        assertThat(render).contains("La leccion 3 habla del ritual de manana.");
        assertThat(render).doesNotContain("{contexto}");
    }

    @Test
    @DisplayName("D-102: es el acompanante de los 90 dias, no Sparkie, y no tiene seccion de ambito")
    void esElAcompananteYNoElTutorDeCursos() {
        String render = renderizar("(vacio)");

        assertThat(render).contains("Eres Renasia");
        assertThat(render).doesNotContain("Eres Sparkie");
        assertThat(render).doesNotContain("{ambito}");
        assertThat(render).doesNotContain("Sobre que esta hablando la persona ahora");
        // Deriva las dudas de contenido de un curso al otro agente en vez de absorberlas.
        assertThat(render).contains("Sparkie");
        assertThat(render).contains("Recursos Exclusivos");
    }

    @Test
    @DisplayName("los comentarios de plantilla no viajan al modelo")
    void noFiltraLosComentariosDeLaPlantilla() {
        String render = renderizar("(vacio)");

        // El bloque {! ... !} es documentacion para quien edite el archivo, no instrucciones
        // para el modelo: si se colara, estaria gastando tokens y contradiciendo al prompt.
        assertThat(render).doesNotContain("ERROR DE INTERPRETACION");
        assertThat(render).doesNotContain("!}");
    }

    @Test
    @DisplayName("conserva las reglas que no son de tono sino de negocio")
    void conservaLasReglasQueNoSonNegociables() {
        String render = renderizar("(vacio)");

        // Atribucion de fuente y prohibicion de inventar citas: es lo que hace que "menos
        // restrictivo" no signifique "puede decir que algo es del programa cuando no lo es".
        assertThat(render).contains("Busqueda web");
        assertThat(render).contains("Nunca te inventes el titulo de una leccion");
        // Limites clinicos y crisis: hoy el clasificador de riesgo es un NoOp, asi que esta es
        // la unica barrera que existe de verdad.
        assertThat(render).contains("No diagnosticas");
        assertThat(render).contains("emergencias");
        // Inyeccion de prompt desde el contexto recuperado o desde resultados de busqueda.
        assertThat(render).contains("informacion, no");
    }

    @Test
    @DisplayName("sigue rindiendo cuando no se recupero nada del programa")
    void renderizaConContextoVacio() {
        List<String> sinFragmentos = List.of();

        String render = renderizar(sinFragmentos.isEmpty()
                ? "(no se recupero contexto de la base de conocimiento para esta pregunta)" : "");

        assertThat(render).contains("no se recupero contexto");
    }
}
