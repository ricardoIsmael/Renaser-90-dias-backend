package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.ReunionesDeMeetPort;
import com.renaser.os.calendar.domain.model.meet.CodigoDeSalaMeet;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import com.renaser.os.calendar.infrastructure.adapter.out.google.LlamadasAGoogle.Respuesta;
import com.renaser.os.calendar.infrastructure.adapter.out.google.TokenDeAccesoDeGoogle.Credencial;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
            return LecturaDeReunion.SIN_REUNION;
        }
        conferencias.sort(Comparator.comparing(c -> c.path("startTime").asText("")));
        List<String> tramos = new ArrayList<>();
        int entradas = 0;
        boolean enPreparacion = false;
        String grabacion = null;
        for (JsonNode conferencia : conferencias) {
            String nombre = conferencia.path("name").asText();
            Map<String, String> nombres = nombresDe(nombre);
            for (JsonNode transcripcion : todas(nombre + "/transcripts", "transcripts")) {
                String estado = transcripcion.path("state").asText("");
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
                    if ("FILE_GENERATED".equals(g.path("state").asText("")) && uri.startsWith("https://")) {
                        grabacion = uri;
                        break;
                    }
                }
            }
        }
        return new LecturaDeReunion(conferencias.size(), entradas, String.join("\n\n---\n\n", tramos), grabacion, enPreparacion);
    }

    /** El nombre de cada participante (solo el que muestra Meet). Por teléfono, nunca el número. */
    private Map<String, String> nombresDe(String conferencia) {
        Map<String, String> nombres = new HashMap<>();
        for (JsonNode p : todas(conferencia + "/participants", "participants")) {
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
