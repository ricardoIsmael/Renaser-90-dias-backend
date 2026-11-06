package com.renaser.os.calendar.domain.model.meet.transcripcion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TranscripcionDeSesionTest {

    private static final Instant FIN = Instant.parse("2026-11-06T01:00:00Z");
    private static final Duration SEIS_MESES = Duration.ofDays(180);
    private static final String TEXTO = "Ana: Buenas noches a todos, empezamos la clase.\nLuis: Gracias, ya estoy listo.";

    private TranscripcionDeSesion nueva(Instant ahora) {
        return TranscripcionDeSesion.nueva(UUID.randomUUID(), UUID.randomUUID(), ahora);
    }

    // spec 047 FR-007, FR-014
    @Test
    @DisplayName("FR-007 y FR-014: con la transcripción lista queda LISTA, con el texto y su fecha de borrado (6 meses)")
    void lista() {
        Instant ahora = FIN.plus(Duration.ofMinutes(20));
        TranscripcionDeSesion t = nueva(ahora);
        t.registrarLectura(new LecturaDeReunion(1, 2, TEXTO, "https://drive.google.com/file/d/x/view", false), FIN, ahora, SEIS_MESES);

        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.LISTA);
        assertThat(t.texto()).isEqualTo(TEXTO);
        assertThat(t.enlaceDeLaGrabacion()).isEqualTo("https://drive.google.com/file/d/x/view");
        assertThat(t.borrarDespuesDe()).isEqualTo(ahora.plus(SEIS_MESES));
        assertThat(t.proximoIntento()).isNull();
        assertThat(t.toString()).as("toString nunca lleva el texto").doesNotContain("Buenas noches");
    }

    // spec 047 FR-008
    @Test
    @DisplayName("FR-008: mientras Google la prepara, o sin reunión todavía, se vuelve a mirar en 10 minutos")
    void esperando() {
        Instant ahora = FIN.plus(Duration.ofMinutes(10));
        TranscripcionDeSesion t = nueva(ahora);
        t.registrarLectura(new LecturaDeReunion(1, 1, TEXTO, null, true), FIN, ahora, SEIS_MESES);
        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.ESPERANDO);
        assertThat(t.proximoIntento()).isEqualTo(ahora.plus(Duration.ofMinutes(10)));
        assertThat(t.texto()).isNull();

        t.registrarLectura(LecturaDeReunion.SIN_REUNION, FIN, ahora, SEIS_MESES);
        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.ESPERANDO);
        assertThat(t.intentos()).isEqualTo(2);
    }

    // spec 047 FR-008
    @Test
    @DisplayName("FR-008: a las 8 horas del fin se cierra: SIN_REUNION si no hubo reunión, SIN_TRANSCRIPCION si la hubo sin texto")
    void ochoHoras() {
        Instant tarde = FIN.plus(Duration.ofHours(8));
        TranscripcionDeSesion sinReunion = nueva(tarde);
        sinReunion.registrarLectura(LecturaDeReunion.SIN_REUNION, FIN, tarde, SEIS_MESES);
        assertThat(sinReunion.estado()).isEqualTo(EstadoDeTranscripcion.SIN_REUNION);
        assertThat(sinReunion.proximoIntento()).isNull();

        TranscripcionDeSesion sinTexto = nueva(tarde);
        sinTexto.registrarLectura(new LecturaDeReunion(2, 0, "Ana: hola", null, false), FIN, tarde, SEIS_MESES);
        assertThat(sinTexto.estado()).as("menos de 40 caracteres no es una transcripción").isEqualTo(EstadoDeTranscripcion.SIN_TRANSCRIPCION);

        TranscripcionDeSesion todaviaEscribiendo = nueva(tarde);
        todaviaEscribiendo.registrarLectura(new LecturaDeReunion(1, 2, TEXTO, null, true), FIN, tarde, SEIS_MESES);
        assertThat(todaviaEscribiendo.estado()).as("pasado el plazo se guarda lo que haya").isEqualTo(EstadoDeTranscripcion.LISTA);
    }

    // spec 047 FR-006
    @Test
    @DisplayName("Edge case: una transcripción enorme se guarda recortada y lo dice")
    void recortada() {
        Instant ahora = FIN.plus(Duration.ofMinutes(30));
        TranscripcionDeSesion t = nueva(ahora);
        t.registrarLectura(new LecturaDeReunion(1, 1, "a".repeat(TranscripcionDeSesion.LARGO_MAXIMO + 10), null, false), FIN, ahora, SEIS_MESES);
        assertThat(t.recortada()).isTrue();
        assertThat(t.largo()).isEqualTo(TranscripcionDeSesion.LARGO_MAXIMO);
    }

    // spec 047 FR-009, FR-019
    @Test
    @DisplayName("FR-009 y FR-019: sin plan queda SIN_TRANSCRIPCION; sin permiso, SIN_PERMISO y se mira otra vez en 6 horas")
    void sinPlanYSinPermiso() {
        Instant ahora = FIN.plus(Duration.ofMinutes(10));
        TranscripcionDeSesion sinPlan = nueva(ahora);
        sinPlan.sinTranscripcionPorLaCuenta(ahora);
        assertThat(sinPlan.estado()).isEqualTo(EstadoDeTranscripcion.SIN_TRANSCRIPCION);

        TranscripcionDeSesion sinPermiso = nueva(ahora);
        sinPermiso.sinPermiso(ahora);
        assertThat(sinPermiso.estado()).isEqualTo(EstadoDeTranscripcion.SIN_PERMISO);
        assertThat(sinPermiso.proximoIntento()).isEqualTo(ahora.plus(Duration.ofHours(6)));
    }

    // spec 047 FR-014, FR-015
    @Test
    @DisplayName("FR-014: borrar vacía el texto y la grabación y deja la constancia")
    void borrar() {
        Instant ahora = FIN.plus(Duration.ofMinutes(30));
        TranscripcionDeSesion t = nueva(ahora);
        t.registrarLectura(new LecturaDeReunion(1, 2, TEXTO, "https://drive.google.com/x", false), FIN, ahora, SEIS_MESES);
        t.borrar(ahora.plus(SEIS_MESES));
        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.BORRADA);
        assertThat(t.texto()).isNull();
        assertThat(t.enlaceDeLaGrabacion()).isNull();
        assertThat(t.borradaEn()).isNotNull();
    }
}
