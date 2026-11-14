package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.ReunionesDeMeetPort;
import com.renaser.os.calendar.domain.model.meet.CodigoDeSalaMeet;
import com.renaser.os.calendar.domain.model.meet.transcripcion.DiagnosticoDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.TranscripcionDeSesion;
import com.renaser.os.calendar.infrastructure.adapter.out.google.LlamadasAGoogle.Respuesta;
import com.renaser.os.calendar.infrastructure.adapter.out.google.TokenDeAccesoDeGoogle.Credencial;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La transcripción de una sesión con la Meet REST API v2 (spec 047, FR-003, FR-004, FR-020). Es el camino que RenaserOs usa en producción con un
 * Gmail sin Workspace ({@code _shared/google.ts}, {@code reunionesDeSala}): las conferencias de la sala por su código, y de cada una sus
 * participantes, sus transcripciones y las frases de cada transcripción, más el enlace a la grabación. Nunca se busca por título en Drive (FR-005).
 *
 * <p>Nada de lo que Google devuelve se escribe en el registro: solo cantidades y códigos HTTP. Un participante por teléfono va como
 * «Participante», nunca con su número. Una entrada o una conferencia rota se pierde ella, no la sesión (FR-020).
 */
@Component
class GoogleMeetReunionesAdapter implements ReunionesDeMeetPort {

    static final String MEET = GoogleCalendarMeetAdapter.MEET;
    /** Tope de páginas por lista, para que una sala con un historial enorme no deje al proceso girando. */
    static final int MAXIMO_DE_PAGINAS = 50;
    private static final String PARTICIPANTE = "Participante";

    private final LlamadasAGoogle llamadas;
    private final TokenDeAccesoDeGoogle tokens;

    GoogleMeetReunionesAdapter(LlamadasAGoogle llamadas, TokenDeAccesoDeGoogle tokens) {
        this.llamadas = llamadas;
        this.tokens = tokens;
    }

    @Override
    public LecturaDeReunion leer(String enlaceDeLaSala, Instant desde, Instant hasta) {
        String codigo = CodigoDeSalaMeet.deEnlace(enlaceDeLaSala)
                .orElseThrow(() -> new GoogleRechazoException("El enlace no es de una sala de Meet.", 0));
        String filtro = "space.meeting_code = \"" + codigo + "\"";
        List<JsonNode> conferencias = new ArrayList<>();
        for (JsonNode c : todas("conferenceRecords?filter=" + UriUtils.encodeQueryParam(filtro, StandardCharsets.UTF_8), "conferenceRecords")) {
            Instant inicio = instante(c.path("startTime").asText(""));
            if (!c.path("name").asText("").isEmpty() && inicio != null && !inicio.isBefore(desde) && !inicio.isAfter(hasta)) {
                conferencias.add(c);
            }
        }
        if (conferencias.isEmpty()) {
            return new LecturaDeReunion(0, 0, "", null, false, DiagnosticoDeReunion.de(List.of(), null, null));
        }
        conferencias.sort(Comparator.comparing(c -> c.path("startTime").asText("")));
        List<String> tramos = new ArrayList<>();
        List<DiagnosticoDeReunion.Conferencia> vistas = new ArrayList<>();
        int entradas = 0;
        boolean enPreparacion = false;
        String grabacion = null;
        for (JsonNode conferencia : conferencias) {
            String nombre = conferencia.path("name").asText();
            List<JsonNode> participantes = todas(nombre + "/participants", "participants");
            Map<String, String> nombres = nombresDe(participantes);
            List<String> estadosDeTranscripciones = new ArrayList<>();
            List<String> estadosDeGrabaciones = new ArrayList<>();
            for (JsonNode transcripcion : todas(nombre + "/transcripts", "transcripts")) {
                String estado = transcripcion.path("state").asText("");
                estadosDeTranscripciones.add(estado.isEmpty() ? "SIN_ESTADO" : estado);
                if (!"FILE_GENERATED".equals(estado)) {
                    enPreparacion = true;
                }
                List<String> frases = new ArrayList<>();
                for (JsonNode e : todas(transcripcion.path("name").asText() + "/entries", "transcriptEntries")) {
                    String texto = e.path("text").asText("").strip();
                    if (!texto.isEmpty()) {
                        frases.add(nombres.getOrDefault(e.path("participant").asText(""), PARTICIPANTE) + ": " + texto);
                    }
                }
                entradas += frases.size();
                if (!frases.isEmpty()) {
                    tramos.add(String.join("\n", frases));
                }
            }
            if (grabacion == null) {
                for (JsonNode g : todas(nombre + "/recordings", "recordings")) {
                    String uri = g.path("driveDestination").path("exportUri").asText("");
                    String estado = g.path("state").asText("");
                    estadosDeGrabaciones.add(estado.isEmpty() ? "SIN_ESTADO" : estado);
                    if ("FILE_GENERATED".equals(estado) && uri.startsWith("https://") && grabacion == null) {
                        grabacion = uri;
                    }
                }
            }
            vistas.add(conferenciaVista(conferencia, participantes, estadosDeGrabaciones, estadosDeTranscripciones));
        }
        String texto = String.join("\n\n---\n\n", tramos);
        DiagnosticoDeReunion diagnostico = texto.strip().length() >= TranscripcionDeSesion.LARGO_MINIMO ? null
                : diagnosticar(vistas, conferencias.get(0).path("space").asText(""));
        return new LecturaDeReunion(conferencias.size(), entradas, texto, grabacion, enPreparacion, diagnostico);
    }

    /** Issue #151: lo que no identifica a nadie de una conexión: cuándo, cuánto duró, cuántos entraron de cada tipo y qué estados dejó Google. */
    private static DiagnosticoDeReunion.Conferencia conferenciaVista(JsonNode conferencia, List<JsonNode> participantes, List<String> grabaciones,
                                                                      List<String> transcripciones) {
        int conCuenta = 0;
        int anonimos = 0;
        int telefono = 0;
        for (JsonNode p : participantes) {
            if (p.has("signedinUser")) {
                conCuenta++;
            } else if (p.has("anonymousUser")) {
                anonimos++;
            } else if (p.has("phoneUser")) {
                telefono++;
            }
        }
        Instant inicio = instante(conferencia.path("startTime").asText(""));
        Instant fin = instante(conferencia.path("endTime").asText(""));
        int minutos = inicio == null || fin == null || fin.isBefore(inicio) ? 0 : (int) Duration.between(inicio, fin).toMinutes();
        return new DiagnosticoDeReunion.Conferencia(inicio, fin, minutos, conCuenta, anonimos, telefono, grabaciones, transcripciones);
    }

    private DiagnosticoDeReunion diagnosticar(List<DiagnosticoDeReunion.Conferencia> vistas, String espacio) {
        String grabacion = null;
        String transcripcion = null;
        if (espacio.startsWith("spaces/")) {
            try {
                Respuesta r = conToken(c -> llamadas.api(HttpMethod.GET, URI.create(MEET + "/" + espacio), c.tokenDeAcceso(), null));
                if (r.ok()) {
                    JsonNode artefactos = r.cuerpo().path("config").path("artifactConfig");
                    grabacion = onOff(artefactos.path("recordingConfig").path("autoRecordingGeneration").asText(""));
                    transcripcion = onOff(artefactos.path("transcriptionConfig").path("autoTranscriptionGeneration").asText(""));
                }
            } catch (CuentaGoogleRevocadaException | GoogleNoRespondeException | GoogleRechazoException e) {
                // El diagnóstico es un extra: sin la configuración de la sala se arma igual, solo que sin ese dato.
            }
        }
        return DiagnosticoDeReunion.de(vistas, grabacion, transcripcion);
    }

    private static String onOff(String valor) {
        return valor.isEmpty() ? null : "ON".equals(valor) ? "ON" : "OFF";
    }

    /** El nombre de cada participante (solo el que muestra Meet). Por teléfono, nunca el número. */
    private static Map<String, String> nombresDe(List<JsonNode> participantes) {
        Map<String, String> nombres = new HashMap<>();
        for (JsonNode p : participantes) {
            String nombre = p.path("signedinUser").path("displayName").asText("");
            if (nombre.isBlank()) {
                nombre = p.path("anonymousUser").path("displayName").asText("");
            }
            nombres.put(p.path("name").asText(""), nombre.isBlank() ? PARTICIPANTE : nombre.strip());
        }
        return nombres;
    }

    private List<JsonNode> todas(String ruta, String campo) {
        List<JsonNode> salida = new ArrayList<>();
        String pagina = "";
        for (int n = 0; n < MAXIMO_DE_PAGINAS; n++) {
            String separador = ruta.contains("?") ? "&" : "?";
            String url = MEET + "/" + ruta + separador + "pageSize=100"
                    + (pagina.isEmpty() ? "" : "&pageToken=" + UriUtils.encodeQueryParam(pagina, StandardCharsets.UTF_8));
            Respuesta r = conToken(c -> llamadas.api(HttpMethod.GET, URI.create(url), c.tokenDeAcceso(), null));
            if (!r.ok()) {
                throw llamadas.falla(r);
            }
            for (JsonNode x : r.cuerpo().path(campo)) {
                if (x.isObject()) {
                    salida.add(x);
                }
            }
            pagina = r.cuerpo().path("nextPageToken").asText("");
            if (pagina.isEmpty()) {
                break;
            }
        }
        return salida;
    }

    private Respuesta conToken(Function<Credencial, Respuesta> llamada) {
        Respuesta respuesta = llamada.apply(tokens.obtener());
        if (respuesta.codigo() == 401) {
            tokens.invalidar();
            respuesta = llamada.apply(tokens.obtener());
        }
        if (respuesta.codigo() == 401) {
            throw new CuentaGoogleRevocadaException("Google rechazó el permiso de la cuenta (código 401).");
        }
        return respuesta;
    }

    private static Instant instante(String texto) {
        try {
            return texto.isEmpty() ? null : Instant.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
