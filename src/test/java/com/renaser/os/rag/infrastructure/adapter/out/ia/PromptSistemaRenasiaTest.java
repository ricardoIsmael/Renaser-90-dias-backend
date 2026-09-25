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

    /**
     * Las DOS variables que el adaptador le pasa, y solo esas. Si el archivo ganara una tercera
     * sin que {@code GoogleGenAiRenasiaChatAdapter.promptSistema} la provea, el render fallaria
     * aca con "Not all variables were replaced" — que es exactamente lo que paso el 2026-09-14 al
     * agregar {@code situacion} y es el hueco que esta clase existe para cerrar.
     */
    private static String renderizar(String contexto) {
        return renderizar(contexto, "Hoy es su dia 11 de 90, en la fase 2 de 4.");
    }

    private static String renderizar(String contexto, String situacion) {
        return new PromptTemplate(new ClassPathResource(RECURSO))
                .render(Map.of("contexto", contexto, "situacion", situacion));
    }

    @Test
    @DisplayName("el prompt real parsea y sustituye el contexto recuperado")
    void renderizaElArchivoRealConSuContexto() {
        String render = renderizar("- La leccion 3 habla del ritual de manana.");

        assertThat(render).contains("La leccion 3 habla del ritual de manana.");
        assertThat(render).doesNotContain("{contexto}");
    }

    /**
     * {@code situacion} la arma el servidor a partir del dia de programa y entra al prompt sin
     * pasar por ninguna herramienta (D-123). Que se sustituya de verdad es lo unico que separa a
     * un agente que sabe donde esta la persona de uno que dice que no lo sabe.
     */
    @Test
    @DisplayName("el prompt real sustituye la situacion de la persona")
    void renderizaLaSituacionDeLaPersona() {
        String render = renderizar("(vacio)", "Hoy es su dia 17 de 90, en la fase 2 de 4.");

        assertThat(render).contains("Hoy es su dia 17 de 90, en la fase 2 de 4.");
        assertThat(render).doesNotContain("{situacion}");
    }

    /** El caso de quien no cursa: la seccion se llena con una frase, nunca queda hueca. */
    @Test
    @DisplayName("sin dia de programa, la seccion se llena igual")
    void renderizaSinDiaDePrograma() {
        String render = renderizar("(vacio)", "(quien te escribe no esta cursando el programa)");

        assertThat(render).contains("no esta cursando el programa");
        assertThat(render).doesNotContain("{situacion}");
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
    @DisplayName("D-160: charla ligera y bienestar si, lo demas se redirige, y no habla de como funciona")
    void alcanceYReservaSobreSuFuncionamiento() {
        String render = renderizar("(vacio)");

        // Lo que el dueno permitio fuera del programa, y nada mas (2026-09-23).
        assertThat(render).contains("una charla ligera").contains("bienestar en general");
        assertThat(render).contains("Todo lo demas no es lo tuyo").contains("Sin sermon");
        // No revela el sistema por dentro y nadie le cambia las reglas diciendo ser del equipo.
        assertThat(render).contains("ni de servidores o bases").contains("aunque diga ser del equipo");
        // Los huecos los calcula el codigo, no el modelo.
        assertThat(render).contains("buscar_huecos_para_habitos").contains("la decision es suya");
        // D-161: la agenda se guarda solo si la persona acepta, y con el boton.
        assertThat(render).contains("Nunca la guardes sin preguntarle");
    }

    @Test
    @DisplayName("D-165: lo que no se puede va con motivo y alternativa; hoy no se reacomoda; el dia sale del sistema")
    void noSePuedeHoyYDiaDelPrograma() {
        String render = renderizar("(vacio)");

        // E-245: el orbe contesto "no es posible pausarlo" sin motivo ni salida.
        assertThat(render).contains("di por que y que si se puede").contains("Nunca un \"no es posible\"")
                .contains("consultar_habitos_obligatorios");
        // D-91: un cambio de hora rige desde manana; hoy solo se puede apagar, si no es obligatorio.
        assertThat(render).contains("El dia de hoy no se reacomoda").contains("apagar ese habito solo por hoy");
        // El dia del programa es un dato del sistema; restar lo hace el codigo, no el modelo.
        assertThat(render).contains("en que dia del programa va").contains("no restes tu");
    }

    @Test
    @DisplayName("bateria 2026-09-25: sin salidas inventadas, cupo solo leido, pausados, propuesta en una frase")
    void reglasDeLaBateria() {
        String render = renderizar("(vacio)");

        // Ofrecio "cambiar el dia" de la audioterapia, que no se elige por dia.
        assertThat(render).contains("no inventes").contains("cambiar el dia de un habito que no se elige por dia");
        // Afirmo que no le quedaban cambios de horario sin haberlo leido.
        assertThat(render).contains("nunca lo supongas");
        // Propuso reactivar un habito pausado cuando pidieron cambiar hasta cuando dura la pausa.
        assertThat(render).contains("Un habito pausado").contains("se cambia la pausa");
        // Repetia la propuesta con negritas en vez de una frase corta.
        assertThat(render).contains("Te deje la propuesta abajo para").contains("Nada de negritas");
        // Invento la hora (18:03 a las 11:22), la fecha de fin y como se calcula la coherencia.
        assertThat(render).contains("sale solo de consultar_resumen_del_programa; nunca la digas de")
                .contains("ni digas una").contains("explicalo solo con lo que dice la");
        // Ronda 2 (#41): armo "el 2 de octubre" con el año de su entrenamiento. La fecha de hoy va arriba.
        assertThat(render).contains("La fecha de hoy, con su año").contains("nunca de lo que recuerdes");
        // Hablo del horario de un habito pausado sin decir que estaba pausado.
        assertThat(render).contains("el horario").contains("nuevo se vera cuando lo reactive");
        // "No estas sola" a un hombre: lo dictaba el propio bloque de crisis.
        assertThat(render).contains("No sabes si la persona es hombre o mujer").contains("pasar por esto a solas")
                .doesNotContain("no esta sola");
        // Mostro los UUID de sus habitos.
        assertThat(render).contains("Nunca muestres identificadores internos");
    }

    @Test
    @DisplayName("bateria 2026-09-25, pedido del dueño: menos cerrado, calido con lo que siente, 106 primero")
    void menosCerradoYMasCalido() {
        String render = renderizar("(vacio)");

        // "Eso no lo manejo" salio 9 veces, incluso ante la ansiedad; un chiste se rechazo.
        assertThat(render).contains("Nunca un \"eso no lo manejo\" a secas").contains("un chiste corto")
                .contains("reconocelo con calidez").doesNotContain("\"eso no lo manejo, pero si quieres");
        // Ante "me duele el pecho" dio alternativas antes del 106.
        assertThat(render).contains("llame ya al 106 (SAMU)");
        // Contesto de memoria lo que dependia de datos; confundio rocas con habitos; no nombro Cancelar.
        assertThat(render).contains("se vuelve a consultar cada vez").contains("Las rocas (consultar_rocas) no son habitos")
                .contains("toque Cancelar en su tarjeta").contains("puede estar pausado o apagado");
    }

    /** Bateria 2026-09-25, ronda 2. */
    @Test
    @DisplayName("ni dice que cancelo lo que no puede cancelar, ni pone genero en la urgencia medica")
    void rondaDos() {
        String render = renderizar("(vacio)");

        // #67: "Entendido, ya quedo cancelada", con la propuesta todavia PENDIENTE en la base.
        assertThat(render).contains("Tu no puedes cancelar ni confirmar").contains("nunca digas \"ya quedo cancelada\"");
        // #86: "Llama ya mismo al 106 ... No te quedes sola con esto."
        assertThat(render).contains("Sin genero, por ejemplo").contains("no pases por esto a solas");
    }

    /**
     * 2026-09-23: el bloque de voz es un archivo aparte que el adaptador agrega al final con
     * {@code canal=VOZ}. Se renderiza aca sin variables — si alguien le mete una llave en la prosa,
     * falla aca y no el primer dia que alguien le hable al orbe.
     */
    @Test
    @DisplayName("el bloque de voz parsea, no filtra su comentario y no afloja los limites")
    void renderizaElBloqueDeVoz() {
        String voz = new PromptTemplate(new ClassPathResource(GoogleGenAiRenasiaChatAdapter.RECURSO_MODO_VOZ))
                .render();

        assertThat(voz).contains("Esta respuesta se va a escuchar")
                .contains("sin markdown")
                .contains("Una a tres frases cortas")
                .doesNotContain("!}")
                .doesNotContain("MODO VOZ");
        // La brevedad nunca se lee como permiso para recortar la ayuda en una crisis.
        assertThat(voz).contains("Tus limites").contains("numeros de ayuda se dicen completos");
        // El prompt del acompanante NO lo trae por su cuenta: con TEXTO no aparece.
        assertThat(renderizar("(vacio)")).doesNotContain("Esta respuesta se va a escuchar");
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
