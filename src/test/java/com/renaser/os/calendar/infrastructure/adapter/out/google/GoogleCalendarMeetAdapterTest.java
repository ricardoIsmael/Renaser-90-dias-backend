package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeCoanfitriones;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeSala;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El adaptador de Google contra un servidor HTTP de mentira (spec 040): lo que Renaser le manda a la Calendar API y a la Meet REST API, y cómo
 * entiende lo que responde. Ninguna llamada sale a la red y ninguna credencial es real.
 */
class GoogleCalendarMeetAdapterTest {

    private static final String EVENTOS = "https://www.googleapis.com/calendar/v3/calendars/" + GoogleDeMentira.CALENDARIO + "/events";
    private static final PedidoDeSala PEDIDO = new PedidoDeSala("0a1b2c3d4e5f4a6b8c7d9e0f1a2b3c4d", "Clase en vivo",
            Instant.parse("2026-11-06T00:00:00Z"), Instant.parse("2026-11-06T01:30:00Z"), ZoneId.of("America/Lima"));
    private static final String ENLACE = "https://meet.google.com/abc-defg-hij";
    private static final ObjectMapper JSON = new ObjectMapper();

    private GoogleDeMentira g;
    private MockRestServiceServer servidor;
    private GoogleCalendarMeetAdapter adapter;
    private final List<Duration> pausas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        g = new GoogleDeMentira();
        servidor = g.servidor;
        adapter = new GoogleCalendarMeetAdapter(g.llamadas, g.tokens, pausas::add);
    }

    private void tokenDeAcceso() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"" + GoogleDeMentira.TOKEN_DE_ACCESO + "\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
    }

    // spec 040 FR-002, FR-003
    @Test
    @DisplayName("FR-002: crear = events.insert con el id derivado, conferenceDataVersion=1, sendUpdates=none y createRequest de hangoutsMeet; el link sale de hangoutLink")
    void crearLaSala() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + GoogleDeMentira.TOKEN_DE_ACCESO))
                .andExpect(jsonPath("$.id").value(PEDIDO.idGoogle()))
                .andExpect(jsonPath("$.summary").value("Clase en vivo"))
                .andExpect(jsonPath("$.start.dateTime").value("2026-11-05T19:00:00-05:00"))
                .andExpect(jsonPath("$.start.timeZone").value("America/Lima"))
                .andExpect(jsonPath("$.end.dateTime").value("2026-11-05T20:30:00-05:00"))
                .andExpect(jsonPath("$.conferenceData.createRequest.conferenceSolutionKey.type").value("hangoutsMeet"))
                .andExpect(jsonPath("$.conferenceData.createRequest.requestId").isNotEmpty())
                .andExpect(jsonPath("$.recurrence").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"x\",\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        ResultadoDeSala resultado = adapter.crear(PEDIDO);

        assertThat(resultado.lista()).isTrue();
        assertThat(resultado.enlace()).isEqualTo(ENLACE);
        servidor.verify();
    }

    // spec 040 FR-022
    @Test
    @DisplayName("FR-022: sin invitados pedidos, a Google solo viajan el id, el título, el inicio, el fin y el acceso a la conferencia: sin descripción, sin nombres")
    void aGoogleNoLeLlegaNingunDatoDePersonas() {
        tokenDeAcceso();
        Set<String> campos = new TreeSet<>();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode cuerpo = JSON.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    cuerpo.fieldNames().forEachRemaining(campos::add);
                })
                .andRespond(withSuccess("{\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        adapter.crear(PEDIDO);

        assertThat(campos).containsExactlyInAnyOrder("id", "summary", "start", "end", "conferenceData");
        assertThat(campos).doesNotContain("attendees", "description", "recurrence", "creator", "organizer");
    }

    // spec 040 FR-003
    @Test
    @DisplayName("FR-003: si no viene hangoutLink, el link sale del punto de entrada de video de la conferencia")
    void elLinkSalePorElPuntoDeEntradaDeVideo() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"conferenceData":{"entryPoints":[{"entryPointType":"phone","uri":"tel:+1"},
                        {"entryPointType":"video","uri":"%s"}]}}""".formatted(ENLACE), MediaType.APPLICATION_JSON));

        assertThat(adapter.crear(PEDIDO).enlace()).isEqualTo(ENLACE);
    }

    // spec 040 FR-006
    @Test
    @DisplayName("FR-006: si Google responde pending se consulta el evento con 1 s de espera hasta que la sala esté lista")
    void respuestaPendiente() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(pendiente(), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pendiente(), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        ResultadoDeSala resultado = adapter.crear(PEDIDO);

        assertThat(resultado.enlace()).isEqualTo(ENLACE);
        assertThat(pausas).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
        servidor.verify();
    }

    // spec 040 FR-006
    @Test
    @DisplayName("FR-006: tras 3 consultas sin respuesta, la sala queda pendiente (no es un error) para que el proceso de fondo la complete")
    void tresConsultasYSeRinde() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(pendiente(), MediaType.APPLICATION_JSON));
        for (int i = 0; i < 3; i++) {
            servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle())).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(pendiente(), MediaType.APPLICATION_JSON));
        }

        ResultadoDeSala resultado = adapter.crear(PEDIDO);

        assertThat(resultado.lista()).isFalse();
        assertThat(pausas).hasSize(3);
        servidor.verify();
    }

    // spec 040 FR-007
    @Test
    @DisplayName("FR-007: si Google ya tenía el evento (409) se toma su sala en vez de crear otra")
    void unConflictoTomaLaSalaQueYaExiste() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":409}}"));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"confirmed\",\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.crear(PEDIDO).enlace()).isEqualTo(ENLACE);
        servidor.verify();
    }

    // spec 040 FR-007
    @Test
    @DisplayName("FR-007: si el evento estaba cancelado en Google se vuelve a confirmar y se le pide una sala nueva")
    void unEventoCanceladoSeReconfirma() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("{}"));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"cancelled\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle() + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.conferenceData.createRequest.conferenceSolutionKey.type").value("hangoutsMeet"))
                .andRespond(withSuccess("{\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.crear(PEDIDO).enlace()).isEqualTo(ENLACE);
        servidor.verify();
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: actualizar = events.patch sin avisar a nadie, y vuelve a confirmar el evento si estaba cancelado; el link no se toca")
    void actualizarElEvento() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle() + "?sendUpdates=none")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.summary").value("Clase en vivo"))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.conferenceData").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.actualizar(PEDIDO);

        servidor.verify();
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: borrar = events.delete sin avisar a nadie; que el evento ya no exista (404 o 410) cuenta como hecho")
    void borrarElEvento() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/uno?sendUpdates=none")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        servidor.expect(requestTo(EVENTOS + "/dos?sendUpdates=none")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo(EVENTOS + "/tres?sendUpdates=none")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.GONE));

        adapter.borrar("uno");
        adapter.borrar("dos");
        adapter.borrar("tres");

        servidor.verify();
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: los 5xx, el 429 y los límites de cuota de Google se reintentan; un 400 o un 403 de permisos, no")
    void fallosQueSeReintentanYFallosQueNo() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo(EVENTOS + "/b?sendUpdates=none")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        servidor.expect(requestTo(EVENTOS + "/c?sendUpdates=none")).andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}"));
        servidor.expect(requestTo(EVENTOS + "/d?sendUpdates=none")).andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"errors\":[{\"reason\":\"forbidden\"}]}}"));
        servidor.expect(requestTo(EVENTOS + "/e?sendUpdates=none")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.borrar("a")).isInstanceOfSatisfying(GoogleNoRespondeException.class, e -> assertThat(e.codigoHttp()).isEqualTo(503));
        assertThatThrownBy(() -> adapter.borrar("b")).isInstanceOfSatisfying(GoogleNoRespondeException.class, e -> assertThat(e.codigoHttp()).isEqualTo(429));
        assertThatThrownBy(() -> adapter.borrar("c")).isInstanceOf(GoogleNoRespondeException.class);
        assertThatThrownBy(() -> adapter.borrar("d")).isInstanceOfSatisfying(GoogleRechazoException.class, e -> assertThat(e.codigoHttp()).isEqualTo(403));
        assertThatThrownBy(() -> adapter.borrar("e")).isInstanceOf(GoogleRechazoException.class);
    }

    // spec 040 FR-008, SC-006
    @Test
    @DisplayName("FR-008: el motivo de un fallo es una frase corta nuestra: ni el cuerpo de lo que respondió Google ni un token")
    void elMotivoNoFiltraElCuerpoDeGoogle() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"datos-privados-de-una-persona " + GoogleDeMentira.REFRESH_TOKEN + "\"}}"));

        assertThatThrownBy(() -> adapter.borrar("a")).hasMessage("Google no aceptó el pedido (código 400).")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("datos-privados", GoogleDeMentira.REFRESH_TOKEN));
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: sin red es un fallo reintentable con código 0, y sin exponer el motivo técnico")
    void sinRed() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(request -> {
            throw new IOException("connection reset por " + GoogleDeMentira.TOKEN_DE_ACCESO);
        });

        assertThatThrownBy(() -> adapter.borrar("a")).isInstanceOfSatisfying(GoogleNoRespondeException.class, e -> {
            assertThat(e.codigoHttp()).isZero();
            assertThat(e.getMessage()).doesNotContain(GoogleDeMentira.TOKEN_DE_ACCESO);
        });
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: un 401 pide un token nuevo una vez; si Google lo vuelve a rechazar, el permiso ya no vale (cuenta revocada)")
    void unCuatrocientosUnoDosVecesEsUnaCuentaRevocada() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.borrar("a")).isInstanceOf(CuentaGoogleRevocadaException.class);
        servidor.verify();
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: un 401 con un token vencido se arregla solo: se pide otro y el pedido sale bien")
    void unTokenVencidoSeRenueva() {
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        tokenDeAcceso();
        servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.NO_CONTENT));

        adapter.borrar("a");

        servidor.verify();
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: sin cuenta o con la cuenta revocada, ni siquiera se llama a Google")
    void sinCuentaNoSeLlamaAGoogle() {
        g.cuenta.actual = null;
        assertThatThrownBy(() -> adapter.borrar("a")).isInstanceOf(CuentaGoogleNoConectadaException.class);

        g.cuenta.actual = com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle.conectar("x@gmail.com", "token", Set.of(),
                com.renaser.os.shared.domain.UserId.of(java.util.UUID.randomUUID()), g.reloj.now());
        g.cuenta.actual.revocar("vencida", g.reloj.now());
        assertThatThrownBy(() -> adapter.borrar("a")).isInstanceOf(CuentaGoogleNoConectadaException.class);

        servidor.verify();
    }

    // spec 040 FR-013
    @Test
    @DisplayName("FR-013: pasado el límite propio de pedidos por minuto, el pedido ni sale y queda para el siguiente turno")
    void elLimitePropioCortaElPedido() {
        GoogleDeMentira poco = new GoogleDeMentira(1);
        poco.servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withSuccess(
                "{\"access_token\":\"t\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        poco.servidor.expect(requestTo(EVENTOS + "/a?sendUpdates=none")).andRespond(withStatus(HttpStatus.NO_CONTENT));
        GoogleCalendarMeetAdapter conPocoMargen = new GoogleCalendarMeetAdapter(poco.llamadas, poco.tokens, pausas::add);

        conPocoMargen.borrar("a");

        assertThatThrownBy(() -> conPocoMargen.borrar("b")).isInstanceOfSatisfying(GoogleNoRespondeException.class,
                e -> assertThat(e.codigoHttp()).isEqualTo(429));
        poco.servidor.verify();
    }

    // spec 040 FR-023, SC-013
    @Test
    @DisplayName("FR-023: dejar la sala ABIERTA = PATCH accessType OPEN con updateMask, y se lee de vuelta lo que Google confirmó")
    void dejarLaSalaAbierta() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.accessType")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.config.accessType").value("OPEN"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"config\":{\"accessType\":\"OPEN\"}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarAcceso(ENLACE, AccesoSalaMeet.ABIERTA)).contains(AccesoSalaMeet.ABIERTA);
        servidor.verify();
    }

    // spec 040 FR-023, SC-013
    @Test
    @DisplayName("FR-023: «con permiso» = accessType TRUSTED, también cuando es el valor por el que ya empezaba: se pide siempre")
    void dejarLaSalaConPermiso() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.accessType")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.config.accessType").value("TRUSTED")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8"))
                .andRespond(withSuccess("{\"config\":{\"accessType\":\"TRUSTED\"}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarAcceso(ENLACE, AccesoSalaMeet.CON_PERMISO)).contains(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: un 200 no prueba que quedó: se devuelve lo que Google dice AL LEER, aunque no sea lo pedido")
    void sePideLoQueGoogleDiceAlLeer() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.accessType")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8"))
                .andRespond(withSuccess("{\"config\":{\"accessType\":\"TRUSTED\"}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarAcceso(ENLACE, AccesoSalaMeet.ABIERTA)).contains(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: si Google no deja cambiarlo (403), se lee igual y se devuelve lo que la sala tiene; el evento y el link no se pierden")
    void siGoogleNoDejaCambiarSeLeeIgual() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.accessType")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8"))
                .andRespond(withSuccess("{\"config\":{\"accessType\":\"TRUSTED\"}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarAcceso(ENLACE, AccesoSalaMeet.ABIERTA)).contains(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: un tipo de acceso que no es ninguno de los dos (RESTRICTED) no se da por confirmado; y un enlace que no es de Meet se rechaza")
    void otrosAccesosYEnlacesQueNoSonDeMeet() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.accessType")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8"))
                .andRespond(withSuccess("{\"config\":{\"accessType\":\"RESTRICTED\"}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarAcceso(ENLACE, AccesoSalaMeet.ABIERTA)).isEqualTo(Optional.empty());
        assertThatThrownBy(() -> adapter.fijarAcceso("https://zoom.us/j/1", AccesoSalaMeet.ABIERTA)).isInstanceOf(GoogleRechazoException.class);
    }

    // ─── Decisión 6 del dueño (2026-11-05): invitado y grabación ───────────────

    private static final String MASCARAS = "config.artifactConfig.recordingConfig.autoRecordingGeneration,"
            + "config.artifactConfig.transcriptionConfig.autoTranscriptionGeneration,"
            + "config.artifactConfig.smartNotesConfig.autoSmartNotesGeneration";

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: el correo de quien crea el evento va en attendees, y Google no le manda correos (sendUpdates=none)")
    void quienCreaVaComoInvitado() {
        tokenDeAcceso();
        PedidoDeSala conInvitado = new PedidoDeSala(PEDIDO.idGoogle(), PEDIDO.titulo(), PEDIDO.inicio(), PEDIDO.fin(), PEDIDO.zona(),
                List.of("admin@renaser.pe"));
        servidor.expect(requestTo(EVENTOS + "?conferenceDataVersion=1&sendUpdates=none")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.attendees.length()").value(1))
                .andExpect(jsonPath("$.attendees[0].email").value("admin@renaser.pe"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andRespond(withSuccess("{\"hangoutLink\":\"" + ENLACE + "\"}", MediaType.APPLICATION_JSON));

        adapter.crear(conInvitado);

        servidor.verify();
    }

    // spec 040 FR-035
    @Test
    @DisplayName("FR-035: al editar con invitados se manda la lista; sin lista (el proceso de fondo) el campo no viaja y Google conserva los que tenía")
    void editarConYSinInvitados() {
        tokenDeAcceso();
        PedidoDeSala conInvitados = new PedidoDeSala(PEDIDO.idGoogle(), PEDIDO.titulo(), PEDIDO.inicio(), PEDIDO.fin(), PEDIDO.zona(),
                List.of("admin@renaser.pe", "alquimista@gmail.com"));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle() + "?sendUpdates=none")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.attendees[1].email").value("alquimista@gmail.com"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(EVENTOS + "/" + PEDIDO.idGoogle() + "?sendUpdates=none")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.attendees").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.actualizar(conInvitados);
        adapter.actualizar(PEDIDO);

        servidor.verify();
    }

    // spec 040 FR-036, SC-014
    @Test
    @DisplayName("FR-036: grabar, transcribir y notas = UN PATCH de config.artifactConfig (ON) con su updateMask, y se lee la sala de vuelta")
    void pedirGrabacionYTranscripcion() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=" + MASCARAS)).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.config.artifactConfig.recordingConfig.autoRecordingGeneration").value("ON"))
                .andExpect(jsonPath("$.config.artifactConfig.transcriptionConfig.autoTranscriptionGeneration").value("ON"))
                .andExpect(jsonPath("$.config.artifactConfig.smartNotesConfig.autoSmartNotesGeneration").value("ON"))
                .andExpect(jsonPath("$.config.accessType").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"config":{"accessType":"OPEN","artifactConfig":{"recordingConfig":{"autoRecordingGeneration":"ON"},
                        "transcriptionConfig":{"autoTranscriptionGeneration":"ON"},"smartNotesConfig":{"autoSmartNotesGeneration":"ON"}}}}""",
                        MediaType.APPLICATION_JSON));

        ArtefactosDeLaSala quedo = adapter.fijarArtefactos(ENLACE, ArtefactosDeLaSala.pedir(true, true, true));

        assertThat(quedo).isEqualTo(new ArtefactosDeLaSala(true, true, true));
        servidor.verify();
    }

    // spec 040 FR-037, SC-015
    @Test
    @DisplayName("FR-037: si Google rechaza el pedido entero (cuenta sin plan) se piden de a uno; lo que no quedó se devuelve en false, sin error")
    void sinPlanSePidenDeAUno() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij"))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=" + MASCARAS)).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":403}}"));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.artifactConfig.recordingConfig.autoRecordingGeneration"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.artifactConfig.transcriptionConfig.autoTranscriptionGeneration"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.artifactConfig.smartNotesConfig.autoSmartNotesGeneration"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"config\":{\"artifactConfig\":{\"recordingConfig\":{\"autoRecordingGeneration\":\"ON\"},"
                        + "\"transcriptionConfig\":{\"autoTranscriptionGeneration\":\"OFF\"}}}}", MediaType.APPLICATION_JSON));

        ArtefactosDeLaSala quedo = adapter.fijarArtefactos(ENLACE, ArtefactosDeLaSala.pedir(true, true, true));

        assertThat(quedo).isEqualTo(new ArtefactosDeLaSala(true, false, false));
        servidor.verify();
    }

    // spec 040 FR-036
    @Test
    @DisplayName("FR-036: un 200 no prueba que quedó: lo que Google dice al leer manda, y lo no pedido no se toca ni se informa")
    void soloLoPedidoYLoLeido() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij"))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8?updateMask=config.artifactConfig.recordingConfig.autoRecordingGeneration"))
                .andExpect(jsonPath("$.config.artifactConfig.transcriptionConfig").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8")).andRespond(withSuccess("{\"config\":{}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fijarArtefactos(ENLACE, ArtefactosDeLaSala.pedir(true, false, false)))
                .isEqualTo(new ArtefactosDeLaSala(false, null, null));
        servidor.verify();
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: lee quiénes ya son coanfitriones y da de alta solo a los que faltan (POST members con role COHOST), sin duplicar")
    void coanfitrionesSoloLosQueFaltan() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij"))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8/members?pageSize=100")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"members\":[{\"name\":\"spaces/Zk3jd8/members/1\",\"email\":\"Admin@Renaser.pe\",\"role\":\"COHOST\"}]}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8/members")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value("mentor@gmail.com")).andExpect(jsonPath("$.role").value("COHOST"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResultadoDeCoanfitriones resultado = adapter.agregarCoanfitriones(ENLACE, List.of("admin@renaser.pe", "mentor@gmail.com"));

        assertThat(resultado).isEqualTo(new ResultadoDeCoanfitriones(2, 2, null));
        servidor.verify();
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: 403 o 404 de Google en un correo no rompe: se cuenta el rechazo con su código y se sigue con los demás")
    void coanfitrionRechazadoSeCuenta() {
        tokenDeAcceso();
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/abc-defg-hij"))
                .andRespond(withSuccess("{\"name\":\"spaces/Zk3jd8\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8/members?pageSize=100"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":403}}"));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8/members")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":403}}"));
        servidor.expect(requestTo("https://meet.googleapis.com/v2/spaces/Zk3jd8/members")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResultadoDeCoanfitriones resultado = adapter.agregarCoanfitriones(ENLACE, List.of("a@x.pe", "b@x.pe"));

        assertThat(resultado).isEqualTo(new ResultadoDeCoanfitriones(2, 1, 403));
        servidor.verify();
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: el permiso meetings.space.created se pide solo con los coanfitriones encendidos y nunca es obligatorio")
    void elPermisoNuevoEsOpcional() {
        ConfiguracionDeGoogle config = g.configuracion;
        assertThat(config.permisosPedidos()).doesNotContain(ConfiguracionDeGoogle.PERMISO_DE_COANFITRIONES);

        config.opcionesDeLaSala(true, true, true, true, true);

        assertThat(config.permisosPedidos()).contains(ConfiguracionDeGoogle.PERMISO_DE_COANFITRIONES);
        assertThat(config.permisosObligatorios()).doesNotContain(ConfiguracionDeGoogle.PERMISO_DE_COANFITRIONES);
    }

    // spec 040 FR-036
    @Test
    @DisplayName("FR-036: si no se pide nada, no se llama a Google")
    void nadaQuePedir() {
        assertThat(adapter.fijarArtefactos(ENLACE, ArtefactosDeLaSala.NADA)).isEqualTo(ArtefactosDeLaSala.NADA);
        servidor.verify();
    }

    private static String pendiente() {
        return "{\"conferenceData\":{\"createRequest\":{\"status\":{\"statusCode\":\"pending\"}}}}";
    }
}
