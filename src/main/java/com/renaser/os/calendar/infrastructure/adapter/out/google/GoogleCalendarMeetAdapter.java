package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.SalaDeReunionPort;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.CodigoDeSalaMeet;
import com.renaser.os.calendar.infrastructure.adapter.out.google.LlamadasAGoogle.Respuesta;
import com.renaser.os.calendar.infrastructure.adapter.out.google.TokenDeAccesoDeGoogle.Credencial;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * La sala de Meet de un evento, con la Calendar API y la Meet REST API (spec 040, FR-002, FR-003, FR-006, FR-007, FR-023). La mecánica es la que
 * RenaserOs ya usa en producción con un Gmail sin Workspace, adaptada a nuestro puerto: {@code events.insert} con
 * {@code conferenceData.createRequest} y {@code conferenceDataVersion=1}.
 *
 * <p><b>Qué viaja a Google</b> (FR-022): el id derivado, el título, cuándo empieza y termina y la zona. Sin invitados ({@code sendUpdates=none}),
 * sin descripción, sin nombres.
 *
 * <p><b>Sin duplicados</b> (FR-007): el id del evento de Google sale del de Renaser, así que crear dos veces lo mismo da 409 y, en vez de crear otro,
 * se toma el que ya existe. Si estaba cancelado en Google, se vuelve a confirmar.
 */
@Component
class GoogleCalendarMeetAdapter implements SalaDeReunionPort {

    static final String CALENDAR = "https://www.googleapis.com/calendar/v3";
    static final String MEET = "https://meet.googleapis.com/v2";
    private static final int MAXIMO_DE_CONSULTAS_SI_ESTA_PENDIENTE = 3;
    private static final Duration ESPERA_ENTRE_CONSULTAS = Duration.ofSeconds(1);
    private static final Duration TOPE_TOTAL_DE_LA_CREACION = Duration.ofSeconds(8);

    private final LlamadasAGoogle llamadas;
    private final TokenDeAccesoDeGoogle tokens;
    private final Pausa pausa;

    /** Esperar entre consultas: aparte para que las pruebas no esperen de verdad. */
    @FunctionalInterface
    interface Pausa {
        void esperar(Duration duracion);
    }

    @Autowired
    GoogleCalendarMeetAdapter(LlamadasAGoogle llamadas, TokenDeAccesoDeGoogle tokens) {
        this(llamadas, tokens, duracion -> {
            try {
                Thread.sleep(duracion.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    GoogleCalendarMeetAdapter(LlamadasAGoogle llamadas, TokenDeAccesoDeGoogle tokens, Pausa pausa) {
        this.llamadas = llamadas;
        this.tokens = tokens;
        this.pausa = pausa;
    }

    @Override
    public ResultadoDeSala crear(PedidoDeSala pedido) {
        long inicio = System.nanoTime();
        Respuesta respuesta = conToken(c -> llamadas.api(HttpMethod.POST,
                URI.create(CALENDAR + "/calendars/" + segmento(c.calendarioId()) + "/events?conferenceDataVersion=1&sendUpdates=none"),
                c.tokenDeAcceso(), cuerpoDeCreacion(pedido)));
        if (respuesta.codigo() == 409) {
            return tomarLaQueYaExiste(pedido, inicio);
        }
        if (!respuesta.ok()) {
            throw llamadas.falla(respuesta);
        }
        return esperarSiHaceFalta(pedido.idGoogle(), resultadoDe(respuesta.cuerpo()), inicio);
    }

    @Override
    public void actualizar(PedidoDeSala pedido) {
        Map<String, Object> cuerpo = cuerpoDelEvento(pedido);
        cuerpo.put("status", "confirmed");
        Respuesta respuesta = conToken(c -> llamadas.api(HttpMethod.PATCH, urlDelEvento(c, pedido.idGoogle(), "?sendUpdates=none"),
                c.tokenDeAcceso(), cuerpo));
        if (!respuesta.ok()) {
            throw llamadas.falla(respuesta);
        }
    }

    @Override
    public void borrar(String idGoogle) {
        Respuesta respuesta = conToken(c -> llamadas.api(HttpMethod.DELETE, urlDelEvento(c, idGoogle, "?sendUpdates=none"),
                c.tokenDeAcceso(), null));
        boolean yaNoExistia = respuesta.codigo() == 404 || respuesta.codigo() == 410;
        if (!respuesta.ok() && !yaNoExistia) {
            throw llamadas.falla(respuesta);
        }
    }

    @Override
    public Optional<AccesoSalaMeet> fijarAcceso(String enlace, AccesoSalaMeet acceso) {
        String codigo = CodigoDeSalaMeet.deEnlace(enlace)
                .orElseThrow(() -> new GoogleRechazoException("El enlace no es de una sala de Meet.", 0));
        String nombre = nombreDeLaSala(codigo);
        RuntimeException noSePudoCambiar = null;
        try {
            Respuesta cambio = conToken(c -> llamadas.api(HttpMethod.PATCH,
                    URI.create(MEET + "/" + nombre + "?updateMask=config.accessType"), c.tokenDeAcceso(),
                    Map.of("config", Map.of("accessType", tipoDeAcceso(acceso)))));
            if (!cambio.ok()) {
                throw llamadas.falla(cambio);
            }
        } catch (GoogleRechazoException rechazo) {
            noSePudoCambiar = rechazo; // Se lee igual: la sala conserva lo que tenia y eso es lo que se confirma.
        }
        Optional<AccesoSalaMeet> leido = accesoDeLaSala(nombre);
        if (noSePudoCambiar != null && leido.isEmpty()) {
            throw noSePudoCambiar;
        }
        return leido;
    }

    // ─── Crear: 409, pendiente ──────────────────────────────────────────────────

    /** Google ya tenía el evento: toma su sala (FR-007). Cancelado, se reconfirma; sin sala, se le pide una. */
    private ResultadoDeSala tomarLaQueYaExiste(PedidoDeSala pedido, long inicio) {
        Respuesta existente = conToken(c -> llamadas.api(HttpMethod.GET, urlDelEvento(c, pedido.idGoogle(), ""), c.tokenDeAcceso(), null));
        if (!existente.ok()) {
            throw llamadas.falla(existente);
        }
        JsonNode evento = existente.cuerpo();
        boolean cancelado = "cancelled".equals(evento.path("status").asText());
        ResultadoDeSala actual = resultadoDe(evento);
        if (!cancelado && (actual.lista() || hayUnaSalaEnCamino(evento))) {
            return esperarSiHaceFalta(pedido.idGoogle(), actual, inicio);
        }
        Map<String, Object> cuerpo = cuerpoDelEvento(pedido);
        cuerpo.put("status", "confirmed");
        cuerpo.put("conferenceData", pedirSala());
        Respuesta parche = conToken(c -> llamadas.api(HttpMethod.PATCH,
                urlDelEvento(c, pedido.idGoogle(), "?conferenceDataVersion=1&sendUpdates=none"), c.tokenDeAcceso(), cuerpo));
        if (!parche.ok()) {
            throw llamadas.falla(parche);
        }
        return esperarSiHaceFalta(pedido.idGoogle(), resultadoDe(parche.cuerpo()), inicio);
    }

    /** FR-006: si Google todavía no terminó, se vuelve a mirar hasta 3 veces con 1 s de espera, dentro de un tope total de 8 s. */
    private ResultadoDeSala esperarSiHaceFalta(String idGoogle, ResultadoDeSala inicial, long inicioNanos) {
        ResultadoDeSala resultado = inicial;
        for (int consulta = 0; consulta < MAXIMO_DE_CONSULTAS_SI_ESTA_PENDIENTE && !resultado.lista(); consulta++) {
            if (Duration.ofNanos(System.nanoTime() - inicioNanos).plus(ESPERA_ENTRE_CONSULTAS).compareTo(TOPE_TOTAL_DE_LA_CREACION) > 0) {
                break;
            }
            pausa.esperar(ESPERA_ENTRE_CONSULTAS);
            Respuesta lectura = conToken(c -> llamadas.api(HttpMethod.GET, urlDelEvento(c, idGoogle, ""), c.tokenDeAcceso(), null));
            if (!lectura.ok()) {
                throw llamadas.falla(lectura);
            }
            if ("failure".equals(estadoDeLaCreacion(lectura.cuerpo()))) {
                throw new GoogleNoRespondeException("Google no pudo crear la sala todavía. Se reintenta solo.", lectura.codigo());
            }
            resultado = resultadoDe(lectura.cuerpo());
        }
        return resultado;
    }

    // ─── Acceso a la sala (Meet REST API) ───────────────────────────────────────

    private String nombreDeLaSala(String codigo) {
        Respuesta sala = conToken(c -> llamadas.api(HttpMethod.GET, URI.create(MEET + "/spaces/" + segmento(codigo)), c.tokenDeAcceso(), null));
        if (!sala.ok()) {
            throw llamadas.falla(sala);
        }
        String nombre = sala.cuerpo().path("name").asText("");
        if (nombre.isEmpty()) {
            throw new GoogleRechazoException("Google no encontró la sala de Meet.", sala.codigo());
        }
        return nombre;
    }

    private Optional<AccesoSalaMeet> accesoDeLaSala(String nombre) {
        Respuesta sala = conToken(c -> llamadas.api(HttpMethod.GET, URI.create(MEET + "/" + nombre), c.tokenDeAcceso(), null));
        if (!sala.ok()) {
            throw llamadas.falla(sala);
        }
        return switch (sala.cuerpo().path("config").path("accessType").asText("")) {
            case "OPEN" -> Optional.of(AccesoSalaMeet.ABIERTA);
            case "TRUSTED" -> Optional.of(AccesoSalaMeet.CON_PERMISO);
            default -> Optional.empty();
        };
    }

    private static String tipoDeAcceso(AccesoSalaMeet acceso) {
        return acceso == AccesoSalaMeet.ABIERTA ? "OPEN" : "TRUSTED";
    }

    // ─── Cuerpos y lectura ──────────────────────────────────────────────────────

    private static Map<String, Object> cuerpoDeCreacion(PedidoDeSala pedido) {
        Map<String, Object> cuerpo = cuerpoDelEvento(pedido);
        cuerpo.put("id", pedido.idGoogle());
        cuerpo.put("conferenceData", pedirSala());
        return cuerpo;
    }

    /** Solo lo que Google necesita para el calendario: título, inicio y fin con su zona. */
    private static Map<String, Object> cuerpoDelEvento(PedidoDeSala pedido) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("summary", pedido.titulo());
        cuerpo.put("start", momento(pedido, true));
        cuerpo.put("end", momento(pedido, false));
        return cuerpo;
    }

    private static Map<String, Object> momento(PedidoDeSala pedido, boolean inicio) {
        var instante = (inicio ? pedido.inicio() : pedido.fin()).atZone(pedido.zona());
        return Map.of("dateTime", instante.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), "timeZone", pedido.zona().getId());
    }

    /** Un conference NUEVO por evento y por fecha, nunca reutilizar el de otro (H2). */
    private static Map<String, Object> pedirSala() {
        return Map.of("createRequest", Map.of("requestId", UUID.randomUUID().toString(),
                "conferenceSolutionKey", Map.of("type", "hangoutsMeet")));
    }

    /** El enlace sale de {@code hangoutLink} o del punto de entrada de video; sin ninguno, la sala todavía no está lista. */
    private static ResultadoDeSala resultadoDe(JsonNode evento) {
        String enlace = evento.path("hangoutLink").asText("");
        if (enlace.isEmpty()) {
            for (JsonNode punto : evento.path("conferenceData").path("entryPoints")) {
                if ("video".equals(punto.path("entryPointType").asText()) && !punto.path("uri").asText("").isEmpty()) {
                    enlace = punto.path("uri").asText();
                    break;
                }
            }
        }
        return enlace.isEmpty() ? ResultadoDeSala.pendiente() : new ResultadoDeSala(enlace);
    }

    private static String estadoDeLaCreacion(JsonNode evento) {
        return evento.path("conferenceData").path("createRequest").path("status").path("statusCode").asText("");
    }

    private static boolean hayUnaSalaEnCamino(JsonNode evento) {
        return "pending".equals(estadoDeLaCreacion(evento));
    }

    // ─── Llamadas ───────────────────────────────────────────────────────────────

    /** Con el token de la cuenta; si Google dice 401 se pide otro token una vez, y si vuelve a decirlo el permiso ya no vale. */
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

    private static URI urlDelEvento(Credencial credencial, String idGoogle, String consulta) {
        return URI.create(CALENDAR + "/calendars/" + segmento(credencial.calendarioId()) + "/events/" + segmento(idGoogle) + consulta);
    }

    private static String segmento(String valor) {
        return UriUtils.encodePathSegment(valor, StandardCharsets.UTF_8);
    }
}
