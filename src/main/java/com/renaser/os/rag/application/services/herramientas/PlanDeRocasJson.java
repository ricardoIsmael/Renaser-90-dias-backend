package com.renaser.os.rag.application.services.herramientas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lee el argumento {@code plan} de {@code proponer_plan_del_dia} y {@code proponer_plan_de_la_semana}
 * (2026-09-23). Los argumentos de una herramienta son {@code Map<String, String>}, asi que una lista
 * de acciones viaja como UN texto JSON; esto lo valida en el borde, estricto, y de aca para adentro
 * se trabaja con tipos.
 *
 * <p><b>Estricto a proposito.</b> Un campo que no esta en el esquema, una clave repetida, texto
 * despues del JSON o una hora que no es {@code HH:MM} se rechazan con un motivo legible en vez de
 * ignorarse: lo que el modelo mando y lo que la persona ve en la propuesta tienen que ser lo mismo.
 *
 * <p><b>Solo forma, ninguna regla.</b> Cuantas acciones por eje, que fechas se pueden planificar o
 * si ya hay plan lo decide {@code rocks} al confirmar. Lo unico que se compara contra algo externo
 * es el nombre del eje, y contra la lista que expone el propio {@code rocks}.
 *
 * <p>{@code objeto}, {@code lista}, {@code eje} y {@code obligatorio} son de paquete (2026-09-23) para
 * que {@link CierreDeSemanaJson} lea el cierre de la semana con las mismas reglas de forma, sin copiarlas.
 */
final class PlanDeRocasJson {

    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private PlanDeRocasJson() {
    }

    /** @param fecha {@code null} si el modelo no la mando: quien llama pone manana */
    record PlanDelDia(LocalDate fecha, List<AccionDelPlan> acciones) {
    }

    /** El motivo ya esta escrito para el modelo: se devuelve tal cual en un {@code Fallo}. */
    static final class PlanMalFormadoException extends RuntimeException {
        PlanMalFormadoException(String motivo) {
            super(motivo);
        }
    }

    static PlanDelDia leerPlanDelDia(String texto, List<String> ejesValidos) {
        JsonNode raiz = objeto(leer(texto), "El plan", Set.of("fecha", "acciones"));
        List<AccionDelPlan> acciones = new ArrayList<>();
        for (JsonNode nodo : lista(raiz, "acciones")) {
            String donde = "La accion " + (acciones.size() + 1);
            JsonNode accion = objeto(nodo, donde, Set.of("eje", "titulo", "inicio", "fin"));
            acciones.add(new AccionDelPlan(eje(accion, donde, ejesValidos), obligatorio(accion, "titulo", donde),
                    hora(accion, "inicio", donde), hora(accion, "fin", donde)));
        }
        return new PlanDelDia(fecha(raiz), List.copyOf(acciones));
    }

    static List<ObjetivoSemanal> leerPlanDeLaSemana(String texto, List<String> ejesValidos) {
        JsonNode raiz = objeto(leer(texto), "El plan", Set.of("objetivos"));
        List<ObjetivoSemanal> objetivos = new ArrayList<>();
        for (JsonNode nodo : lista(raiz, "objetivos")) {
            String donde = "El objetivo " + (objetivos.size() + 1);
            JsonNode objetivo = objeto(nodo, donde, Set.of("eje", "titulo", "obstaculo", "contingencia"));
            objetivos.add(new ObjetivoSemanal(eje(objetivo, donde, ejesValidos),
                    obligatorio(objetivo, "titulo", donde), opcional(objetivo, "obstaculo", donde),
                    opcional(objetivo, "contingencia", donde)));
        }
        return List.copyOf(objetivos);
    }

    private static JsonNode leer(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new PlanMalFormadoException("Falta el plan.");
        }
        try {
            return JSON.readTree(texto);
        } catch (JsonProcessingException noEsJson) {
            throw new PlanMalFormadoException("El plan no es un JSON valido: mandalo como texto con el formato "
                    + "que indica la descripcion de la herramienta.");
        }
    }

    static JsonNode objeto(JsonNode nodo, String donde, Set<String> camposPermitidos) {
        if (nodo == null || !nodo.isObject()) {
            throw new PlanMalFormadoException(donde + " tiene que ser un objeto JSON.");
        }
        for (Iterator<String> campos = nodo.fieldNames(); campos.hasNext(); ) {
            String campo = campos.next();
            if (!camposPermitidos.contains(campo)) {
                throw new PlanMalFormadoException(donde + " tiene un campo que no se usa: '" + campo
                        + "'. Solo se aceptan: " + String.join(", ", camposPermitidos.stream().sorted().toList()) + ".");
            }
        }
        return nodo;
    }

    static JsonNode lista(JsonNode raiz, String campo) {
        JsonNode lista = raiz.get(campo);
        if (lista == null || !lista.isArray() || lista.isEmpty()) {
            throw new PlanMalFormadoException("Falta '" + campo + "': una lista con al menos un elemento.");
        }
        return lista;
    }

    static String eje(JsonNode nodo, String donde, List<String> ejesValidos) {
        String eje = obligatorio(nodo, "eje", donde).toUpperCase(Locale.ROOT);
        if (!ejesValidos.contains(eje)) {
            throw new PlanMalFormadoException(donde + " tiene un eje que no existe: '" + eje + "'. Usa uno de: "
                    + String.join(", ", ejesValidos) + ".");
        }
        return eje;
    }

    static String obligatorio(JsonNode nodo, String campo, String donde) {
        String valor = opcional(nodo, campo, donde);
        if (valor == null) {
            throw new PlanMalFormadoException(donde + " no tiene '" + campo + "'.");
        }
        return valor;
    }

    /** {@code null} si no viene, viene {@code null} o viene en blanco; recortado si viene. */
    private static String opcional(JsonNode nodo, String campo, String donde) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isTextual()) {
            throw new PlanMalFormadoException(donde + ": '" + campo + "' tiene que ser un texto.");
        }
        String limpio = valor.textValue().trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static LocalTime hora(JsonNode nodo, String campo, String donde) {
        String texto = opcional(nodo, campo, donde);
        if (texto == null) {
            return null;
        }
        try {
            return LocalTime.parse(texto, HORA);
        } catch (DateTimeParseException noEsHora) {
            throw new PlanMalFormadoException(donde + ": '" + campo + "' tiene que ir como HH:MM (por ejemplo 07:30).");
        }
    }

    private static LocalDate fecha(JsonNode raiz) {
        String texto = opcional(raiz, "fecha", "El plan");
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException noEsFecha) {
            throw new PlanMalFormadoException("La fecha tiene que ir como AAAA-MM-DD (por ejemplo 2026-09-24).");
        }
    }
}
