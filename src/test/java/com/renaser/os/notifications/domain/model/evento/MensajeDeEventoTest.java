package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El recordatorio como MENSAJE (spec 041, FR-001 y FR-017): el texto del chat y los cinco huecos de la plantilla de WhatsApp, con el «cuando» que dice la app.
 * La hora se dice en la zona del evento (Lima), no en UTC: un evento a las 19:00 de Lima es 00:00 UTC del dia siguiente (regla 02).
 */
class MensajeDeEventoTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 19:00 en Lima del 6 de noviembre = 00:00 UTC del 7. */
    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");
    private static final String MEET = "https://meet.google.com/abc-defg-hij";

    private static AvisoDeEvento aviso() {
        return new AvisoDeEvento(10L, UUID.randomUUID(), "Clase de manifestación", INICIO, LIMA, false);
    }

    // spec 041 FR-001, FR-017
    @Test
    @DisplayName("a 10 minutos: «empieza en 10 min, a las 19:00» y la linea del enlace")
    void aDiezMinutos() {
        var mensaje = new MensajeDeEvento(aviso(), MEET, null);

        assertThat(mensaje.textoParaElChat(INICIO.minusSeconds(600)))
                .isEqualTo("Clase de manifestación\nEmpieza en 10 min, a las 19:00.\nEntra aquí: " + MEET);
        assertThat(mensaje.huecosDeWhatsApp("Rosa", "Escuela Renaser", INICIO.minusSeconds(600)))
                .containsExactly("Rosa", "Escuela Renaser", "Clase de manifestación", "empieza en 10 min, a las 19:00", "Entra aquí: " + MEET);
    }

    // spec 041 FR-017
    @Test
    @DisplayName("a 30 minutos: «empieza en 30 min, a las 19:00»")
    void aTreintaMinutos() {
        assertThat(new MensajeDeEvento(aviso(), MEET, null).huecosDeWhatsApp("Rosa", "Escuela Renaser", INICIO.minusSeconds(1800)).get(3))
                .isEqualTo("empieza en 30 min, a las 19:00");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("a 1 hora justa se dice «empieza en 1 hora, a las 19:00» y no «60 min»")
    void aUnaHora() {
        var mensaje = new MensajeDeEvento(aviso(), MEET, null);

        assertThat(mensaje.huecosDeWhatsApp("Rosa", "Escuela Renaser", INICIO.minusSeconds(3600)).get(3)).isEqualTo("empieza en 1 hora, a las 19:00");
        assertThat(mensaje.textoParaElChat(INICIO.minusSeconds(3600))).contains("Empieza en 1 hora, a las 19:00.");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("un dia antes: «es mañana a las 19:00», con el dia de Lima y no el de UTC")
    void unDiaAntes() {
        Instant elDiaAntes = INICIO.minusSeconds(86_400);

        assertThat(new MensajeDeEvento(aviso(), MEET, null).huecosDeWhatsApp("Rosa", "Escuela Renaser", elDiaAntes).get(3))
                .isEqualTo("es mañana a las 19:00");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("sin enlace, un evento presencial dice «Lugar: …»")
    void unEventoPresencialDiceElLugar() {
        var mensaje = new MensajeDeEvento(aviso(), null, "Av. Ejército 123, Arequipa");

        assertThat(mensaje.huecosDeWhatsApp("Rosa", "Escuela Renaser", INICIO.minusSeconds(600)).get(4)).isEqualTo("Lugar: Av. Ejército 123, Arequipa");
        assertThat(mensaje.textoParaElChat(INICIO.minusSeconds(600))).endsWith("Lugar: Av. Ejército 123, Arequipa");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("sin enlace ni direccion el ultimo hueco manda a mirarlo en Renaser, y el chat no agrega linea")
    void sinEnlaceNiDireccion() {
        var mensaje = new MensajeDeEvento(aviso(), null, null);

        assertThat(mensaje.huecosDeWhatsApp("Rosa", "Escuela Renaser", INICIO.minusSeconds(600)).get(4)).isEqualTo("Míralo en Renaser.");
        assertThat(mensaje.textoParaElChat(INICIO.minusSeconds(600))).doesNotContain("Entra aquí").doesNotContain("Lugar");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("Meta rechaza saltos de linea y mas de cuatro espacios: cada hueco se limpia, y siempre son cinco")
    void cadaHuecoSeLimpia() {
        var sucio = new AvisoDeEvento(10L, UUID.randomUUID(), "Clase\n  de   manifestación", INICIO, LIMA, false);

        List<String> huecos = new MensajeDeEvento(sucio, MEET + "\n", null).huecosDeWhatsApp("  Rosa \t María ", "Escuela Renaser", INICIO.minusSeconds(600));

        assertThat(huecos).hasSize(5);
        assertThat(huecos).allSatisfy(h -> assertThat(h).doesNotContain("\n").doesNotContain("\t").doesNotContain("  "));
        assertThat(huecos.get(0)).isEqualTo("Rosa María");
        assertThat(huecos.get(2)).isEqualTo("Clase de manifestación");
    }

    // spec 041 FR-032
    @Test
    @DisplayName("el nombre de la formacion va tal cual en el segundo hueco: el Diplomado nunca lleva el texto de la Escuela")
    void elNombreDeLaFormacionVaEnElSegundoHueco() {
        var mensaje = new MensajeDeEvento(aviso(), MEET, null);

        assertThat(mensaje.huecosDeWhatsApp("Rosa", "Diplomado Renaser", INICIO.minusSeconds(600)).get(1)).isEqualTo("Diplomado Renaser")
                .doesNotContain("Escuela");
    }
}
