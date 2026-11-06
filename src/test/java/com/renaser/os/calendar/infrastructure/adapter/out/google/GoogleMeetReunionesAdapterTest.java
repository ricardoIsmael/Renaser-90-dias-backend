package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** La Meet REST API simulada (spec 047): qué se pide, en qué orden, y cómo se arma la transcripción. */
class GoogleMeetReunionesAdapterTest {

    private static final String ENLACE = "https://meet.google.com/abc-defg-hij";
    private static final String MEET = "https://meet.googleapis.com/v2";
    private static final String CONFERENCIAS = MEET + "/conferenceRecords?filter=space.meeting_code%20%3D%20%22abc-defg-hij%22&pageSize=100";
    private static final Instant DESDE = Instant.parse("2026-11-06T00:30:00Z");
    private static final Instant HASTA = Instant.parse("2026-11-06T05:00:00Z");

    private GoogleDeMentira g;
    private MockRestServiceServer servidor;
    private GoogleMeetReunionesAdapter adapter;

    @BeforeEach
    void setUp() {
        g = new GoogleDeMentira();
        servidor = g.servidor;
        adapter = new GoogleMeetReunionesAdapter(g.llamadas, g.tokens);
        servidor.expect(ExpectedCount.manyTimes(), requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"" + GoogleDeMentira.TOKEN_DE_ACCESO + "\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
    }

    private void responde(String url, String json) {
        servidor.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + GoogleDeMentira.TOKEN_DE_ACCESO))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    // spec 047 FR-003, FR-004, FR-005, FR-020
    @Test
    @DisplayName("FR-003: conferencias por el código de la sala, solo las de la ventana; nombres, frases en orden y la grabación; nada de Drive")
    void leeLaTranscripcion() {
        responde(CONFERENCIAS, """
                {"conferenceRecords":[
                  {"name":"conferenceRecords/c2","startTime":"2026-11-06T02:10:00Z"},
                  {"name":"conferenceRecords/vieja","startTime":"2026-10-30T01:00:00Z"},
                  {"name":"conferenceRecords/c1","startTime":"2026-11-06T01:00:00Z"},
                  "basura"
                ]}""");
        responde(MEET + "/conferenceRecords/c1/participants?pageSize=100", """
                {"participants":[{"name":"conferenceRecords/c1/participants/p1","signedinUser":{"displayName":"Ana Ruiz"}},
                                  {"name":"conferenceRecords/c1/participants/p2","phoneUser":{"displayName":"+51 9** *** 123"}}]}""");
        responde(MEET + "/conferenceRecords/c1/transcripts?pageSize=100",
                "{\"transcripts\":[{\"name\":\"conferenceRecords/c1/transcripts/t1\",\"state\":\"FILE_GENERATED\"}]}");
        responde(MEET + "/conferenceRecords/c1/transcripts/t1/entries?pageSize=100", """
                {"transcriptEntries":[{"participant":"conferenceRecords/c1/participants/p1","text":"Buenas noches a todos."}],
                 "nextPageToken":"pag2"}""");
        responde(MEET + "/conferenceRecords/c1/transcripts/t1/entries?pageSize=100&pageToken=pag2", """
                {"transcriptEntries":[{"participant":"conferenceRecords/c1/participants/p2","text":"Hola, entro por teléfono."},
                                      {"participant":"conferenceRecords/c1/participants/p1","text":"   "}]}""");
        responde(MEET + "/conferenceRecords/c1/recordings?pageSize=100",
                "{\"recordings\":[{\"state\":\"FILE_GENERATED\",\"driveDestination\":{\"exportUri\":\"https://drive.google.com/file/d/r1/view\"}}]}");
        responde(MEET + "/conferenceRecords/c2/participants?pageSize=100", "{}");
        responde(MEET + "/conferenceRecords/c2/transcripts?pageSize=100",
                "{\"transcripts\":[{\"name\":\"conferenceRecords/c2/transcripts/t2\",\"state\":\"FILE_GENERATED\"}]}");
        responde(MEET + "/conferenceRecords/c2/transcripts/t2/entries?pageSize=100",
                "{\"transcriptEntries\":[{\"participant\":\"conferenceRecords/c2/participants/x\",\"text\":\"Volví a entrar.\"}]}");

        LecturaDeReunion l = adapter.leer(ENLACE, DESDE, HASTA);

        assertThat(l.conferencias()).isEqualTo(2);
        assertThat(l.entradas()).isEqualTo(3);
        assertThat(l.texto()).isEqualTo("Ana Ruiz: Buenas noches a todos.\nParticipante: Hola, entro por teléfono.\n\n---\n\nParticipante: Volví a entrar.");
        assertThat(l.texto()).as("nunca el número de un participante por teléfono").doesNotContain("+51");
        assertThat(l.enlaceDeLaGrabacion()).isEqualTo("https://drive.google.com/file/d/r1/view");
        assertThat(l.transcripcionEnPreparacion()).isFalse();
        servidor.verify();
    }

    // spec 047 FR-003, FR-008
    @Test
    @DisplayName("FR-008: sin conferencias en la ventana no se pide nada más; una transcripción a medio escribir se marca en preparación")
    void sinReunionYEnPreparacion() {
        responde(CONFERENCIAS, "{\"conferenceRecords\":[]}");
        assertThat(adapter.leer(ENLACE, DESDE, HASTA)).isEqualTo(LecturaDeReunion.SIN_REUNION);
        servidor.verify();

        servidor.reset();
        setUp();
        responde(CONFERENCIAS, "{\"conferenceRecords\":[{\"name\":\"conferenceRecords/c1\",\"startTime\":\"2026-11-06T01:00:00Z\"}]}");
        responde(MEET + "/conferenceRecords/c1/participants?pageSize=100", "{}");
        responde(MEET + "/conferenceRecords/c1/transcripts?pageSize=100",
                "{\"transcripts\":[{\"name\":\"conferenceRecords/c1/transcripts/t1\",\"state\":\"ENDED\"}]}");
        responde(MEET + "/conferenceRecords/c1/transcripts/t1/entries?pageSize=100", "{}");
        responde(MEET + "/conferenceRecords/c1/recordings?pageSize=100", "{}");
        assertThat(adapter.leer(ENLACE, DESDE, HASTA).transcripcionEnPreparacion()).isTrue();
    }

    // spec 047 FR-008, FR-019
    @Test
    @DisplayName("FR-008 y FR-019: 403 sin permiso, 429 para reintentar y 401 dos veces es una cuenta revocada")
    void fallos() {
        servidor.expect(requestTo(CONFERENCIAS)).andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));
        assertThatThrownBy(() -> adapter.leer(ENLACE, DESDE, HASTA)).isInstanceOf(GoogleRechazoException.class)
                .satisfies(e -> assertThat(((GoogleRechazoException) e).codigoHttp()).isEqualTo(403));

        servidor.reset();
        setUp();
        servidor.expect(requestTo(CONFERENCIAS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> adapter.leer(ENLACE, DESDE, HASTA)).isInstanceOf(GoogleNoRespondeException.class);

        servidor.reset();
        setUp();
        servidor.expect(ExpectedCount.twice(), requestTo(CONFERENCIAS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> adapter.leer(ENLACE, DESDE, HASTA)).isInstanceOf(CuentaGoogleRevocadaException.class);
    }

    // spec 047 FR-005
    @Test
    @DisplayName("FR-005: un enlace que no es de Meet no llama a Google")
    void enlaceQueNoEsDeMeet() {
        assertThatThrownBy(() -> adapter.leer("https://zoom.us/j/123", DESDE, HASTA)).isInstanceOf(GoogleRechazoException.class);
    }
}
