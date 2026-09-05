package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Una {@link DefinicionHerramienta} del dominio, hablada en el idioma de Spring AI.
 *
 * <p><b>Esta clase es la pieza que faltaba.</b> Las herramientas estaban definidas, probadas y
 * viajando dentro de {@code ChatIAPort.Consulta} desde el 2026-09-05, pero el adaptador de Gemini
 * no las leia: nunca se las declaraba al modelo. El sintoma que lo delato es una respuesta real a
 * un aprendiz que pidio su lista de habitos — <i>"no tengo acceso directo a tu cuenta personal,
 * abre la aplicacion y busca la opcion Plan"</i>. El modelo no mentia: no tenia con que mirar.
 *
 * <p><b>El {@code actorId} se fija al construir el callback, no lo manda el modelo.</b> Es la
 * unica forma de que "complete el habito de Juan" no sea ejecutable: el nombre del duenio de los
 * datos no es un argumento que el modelo pueda emitir, es parte de la identidad de la conversacion.
 * Un callback vive lo que vive una consulta, y solo sabe operar sobre esa persona.
 *
 * <p>Un fallo NO se lanza como excepcion: se le devuelve al modelo como texto, para que se lo
 * explique a la persona. Si un habito ya vencio, la respuesta util es "ese ya no se puede marcar",
 * no que el asistente se quede mudo a mitad de la frase.
 */
class HerramientaToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(HerramientaToolCallback.class);

    /** Sin parametros el esquema igual tiene que ser un objeto JSON valido, o el SDK lo rechaza. */
    private static final String ESQUEMA_SIN_PARAMETROS = "{\"type\":\"object\",\"properties\":{}}";

    private final DefinicionHerramienta definicion;
    private final EjecutarHerramientaAgenteUseCase ejecutarUseCase;
    private final UserId actorId;
    private final ObjectMapper json;

    HerramientaToolCallback(DefinicionHerramienta definicion, EjecutarHerramientaAgenteUseCase ejecutarUseCase,
                             UserId actorId, ObjectMapper json) {
        this.definicion = Objects.requireNonNull(definicion, "definicion es obligatoria");
        this.ejecutarUseCase = Objects.requireNonNull(ejecutarUseCase, "ejecutarUseCase es obligatorio");
        this.actorId = Objects.requireNonNull(actorId, "actorId es obligatorio");
        this.json = Objects.requireNonNull(json, "json es obligatorio");
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(definicion.nombre())
                .description(definicion.descripcion())
                .inputSchema(esquemaDeEntrada())
                .build();
    }

    /**
     * El resultado vuelve al modelo como OBJETO JSON, no como texto plano.
     *
     * <p>No es una preferencia de estilo: Gemini modela la respuesta de una funcion como un
     * {@code Struct}, y el adaptador de Spring AI hace {@code parseJsonToMap(...)} sobre lo que
     * devuelve este metodo antes de mandarlo. Devolviendo texto suelto, la conversacion entera
     * muere con:
     *
     * <pre>
     * RuntimeException: Failed to parse JSON: id=93ef82a1-... | ULTIMA COMIDA DEL DIA | ...
     *   at GoogleGenAiChatModel.parseJsonToMap(GoogleGenAiChatModel.java:368)
     * </pre>
     *
     * Y el detalle cruel es que el fallo ocurre DESPUES de que todo lo dificil salio bien: el
     * modelo pidio la herramienta, el actor se resolvio, los habitos se leyeron. Se rompia al
     * empaquetar la respuesta.
     *
     * <p>El texto del dominio viaja dentro de {@code resultado} y el exito o fallo en {@code ok},
     * para que el modelo pueda distinguir "esto es lo que pediste" de "no se pudo, explicaselo".
     */
    @Override
    public String call(String argumentosJson) {
        InvocacionHerramienta invocacion = new InvocacionHerramienta(definicion.nombre(), comoArgumentos(argumentosJson));
        ResultadoHerramienta resultado = ejecutarUseCase.ejecutar(actorId, invocacion);
        return switch (resultado) {
            case ResultadoHerramienta.Exito exito -> comoObjetoJson(true, exito.contenido());
            // El motivo ya viene escrito para que lo lea una persona (ver ResultadoHerramienta).
            case ResultadoHerramienta.Fallo fallo -> comoObjetoJson(false, fallo.motivo());
        };
    }

    /** Serializa con Jackson y no a mano: el contenido lleva saltos de linea, tildes y comillas. */
    private String comoObjetoJson(boolean ok, String contenido) {
        try {
            return json.writeValueAsString(Map.of("ok", ok, "resultado", contenido));
        } catch (Exception e) {
            log.error("No se pudo serializar el resultado de la herramienta {}", definicion.nombre(), e);
            return "{\"ok\":false,\"resultado\":\"No se pudo leer el resultado de la herramienta.\"}";
        }
    }

    /**
     * JSON Schema de los parametros. Es lo unico que el modelo lee para saber que mandar, asi que
     * la descripcion de cada parametro viaja tal cual la escribio el dominio.
     *
     * <p>Todo se declara {@code string}, incluidos los enteros: los argumentos que emite un modelo
     * llegan como texto de todas formas, y {@code InvocacionHerramienta} los guarda como texto. Un
     * esquema que promete {@code integer} y recibe {@code "3"} genera un rechazo que no aporta —
     * la validacion real de tipo la hace el caso de uso, que sabe que hacer con un valor invalido.
     */
    private String esquemaDeEntrada() {
        if (definicion.parametros().isEmpty()) {
            return ESQUEMA_SIN_PARAMETROS;
        }
        Map<String, Object> propiedades = new LinkedHashMap<>();
        var obligatorios = definicion.parametros().stream()
                .filter(ParametroHerramienta::obligatorio)
                .map(ParametroHerramienta::nombre)
                .toList();
        for (ParametroHerramienta parametro : definicion.parametros()) {
            propiedades.put(parametro.nombre(),
                    Map.of("type", "string", "description", parametro.descripcion()));
        }
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("properties", propiedades);
        esquema.put("required", obligatorios);
        try {
            return json.writeValueAsString(esquema);
        } catch (Exception e) {
            // No deberia pasar: son mapas de String. Si pasa, mejor una herramienta sin parametros
            // declarados que romper la conversacion entera.
            log.error("No se pudo serializar el esquema de la herramienta {}", definicion.nombre(), e);
            return ESQUEMA_SIN_PARAMETROS;
        }
    }

    /**
     * Los argumentos que emite el modelo, como texto plano.
     *
     * <p>Un modelo manda JSON mal formado con total naturalidad, y un `null` o un objeto anidado
     * tambien. Nada de eso puede tumbar la conversacion: se devuelve lo que se pudo leer y el caso
     * de uso decide — {@code obligatoriosFaltantesEn} ya existe justamente para ese momento.
     */
    private Map<String, String> comoArgumentos(String argumentosJson) {
        if (argumentosJson == null || argumentosJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<?, ?> crudo = json.readValue(argumentosJson, Map.class);
            Map<String, String> argumentos = new LinkedHashMap<>();
            crudo.forEach((clave, valor) -> {
                if (clave != null && valor != null) {
                    argumentos.put(clave.toString(), valor.toString());
                }
            });
            return argumentos;
        } catch (Exception e) {
            log.warn("El modelo mando argumentos ilegibles para la herramienta {}", definicion.nombre(), e);
            return Map.of();
        }
    }
}
