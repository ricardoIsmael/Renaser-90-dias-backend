package com.renaser.os.notifications.domain.model.habito;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Texto, etiqueta y vigencia del aviso de un habito (spec 007, FR-012, FR-014, FR-015, FR-016). Sin Spring.
 *
 * <p><b>Los relojes y los momentos estan a proposito en la madrugada UTC</b> (regla 03): las 21:00 de
 * Lima son las 02:00 UTC del dia SIGUIENTE. Un texto armado con la hora UTC diria "02:00" y la persona
 * recibiria un aviso de una hora que no es la de su habito.
 */
class AvisoDeHabitoTest {

    private static final UUID HABITO = UUID.fromString("66507383-7219-43ab-aa42-2fbc76152b82");
    private static final String LIMA = "America/Lima";
    /** 21:00 del 5 de septiembre en Lima. */
    private static final Instant A_LAS_21_EN_LIMA = Instant.parse("2026-09-06T02:00:00Z");
    /** 22:30 del 5 de septiembre en Lima. */
    private static final Instant A_LAS_22_30_EN_LIMA = Instant.parse("2026-09-06T03:30:00Z");
    /** El instante del barrido: 20:50 en Lima. */
    private static final Instant EMITIDO = Instant.parse("2026-09-06T01:50:00Z");

    private static AvisoDeHabito inicio(Instant momento, String zona) {
        return new AvisoDeHabito("INICIO", "Meditación nocturna", 10, 10, HABITO, EMITIDO, momento, zona);
    }

    private static AvisoDeHabito porVencer(Instant momento, String zona) {
        return new AvisoDeHabito("POR_VENCER", "Meditación nocturna", 25, 10, HABITO, EMITIDO, momento, zona);
    }

    // ─── Texto: tildes y tuteo (FR-016) ────────────────────────────────────────────────────

    @Test
    @DisplayName("los titulos llevan tildes y tuteo")
    void titulos() {
        assertThat(inicio(A_LAS_21_EN_LIMA, LIMA).titulo()).isEqualTo("Tu hábito está por empezar");
        assertThat(porVencer(A_LAS_22_30_EN_LIMA, LIMA).titulo()).isEqualTo("Se te vence un hábito");
    }

    // ─── La hora del habito, en la zona del programa (FR-012) ──────────────────────────────

    @Test
    @DisplayName("inicio: dice la hora de Lima (21:00), no la UTC (02:00) ni minutos del barrido")
    void cuerpoDelInicioConLaHoraDeLima() {
        assertThat(inicio(A_LAS_21_EN_LIMA, LIMA).cuerpo())
                .isEqualTo("Meditación nocturna: te toca a las 21:00. En juego: 10 puntos.");
    }

    @Test
    @DisplayName("vencimiento: dice la hora de Lima a la que vence el plazo")
    void cuerpoDelVencimientoConLaHoraDeLima() {
        assertThat(porVencer(A_LAS_22_30_EN_LIMA, LIMA).cuerpo())
                .isEqualTo("Meditación nocturna vence a las 22:30. En juego: 10 puntos.");
    }

    @Test
    @DisplayName("la hora sale en la zona del participante: la misma hora absoluta en otra zona dice otra hora")
    void laHoraDependeDeLaZona() {
        assertThat(inicio(A_LAS_21_EN_LIMA, "America/Bogota").cuerpo()).contains("te toca a las 21:00.");
        assertThat(inicio(A_LAS_21_EN_LIMA, "America/Mexico_City").cuerpo()).contains("te toca a las 20:00.");
    }

    @Test
    @DisplayName("evento viejo del outbox (sin momento): el texto de minutos de siempre, con tildes")
    void sinMomentoUsaLosMinutos() {
        assertThat(inicio(null, LIMA).cuerpo())
                .isEqualTo("Meditación nocturna empieza en 10 min. En juego: 10 puntos.");
        assertThat(porVencer(null, null).cuerpo())
                .isEqualTo("Meditación nocturna vence en 25 min. En juego: 10 puntos.");
    }

    @Test
    @DisplayName("evento viejo (sin zona): tampoco se inventa una hora, se dicen los minutos")
    void sinZonaUsaLosMinutos() {
        assertThat(inicio(A_LAS_21_EN_LIMA, null).cuerpo())
                .isEqualTo("Meditación nocturna empieza en 10 min. En juego: 10 puntos.");
    }

    // ─── Etiqueta compartida con la alarma local (FR-014) ──────────────────────────────────

    @Test
    @DisplayName("la etiqueta es habito-{id}-inicio o habito-{id}-por-vencer")
    void etiquetaPorTipo() {
        assertThat(inicio(A_LAS_21_EN_LIMA, LIMA).etiqueta()).isEqualTo("habito-" + HABITO + "-inicio");
        assertThat(porVencer(A_LAS_22_30_EN_LIMA, LIMA).etiqueta()).isEqualTo("habito-" + HABITO + "-por-vencer");
    }

    @Test
    @DisplayName("sin habito (evento anterior a D-218) no hay etiqueta: cada aviso es uno aparte")
    void sinHabitoNoHayEtiqueta() {
        AvisoDeHabito viejo = new AvisoDeHabito("INICIO", "Meditación nocturna", 10, 10, null, EMITIDO, null, null);

        assertThat(viejo.etiqueta()).isNull();
    }

    // ─── Un aviso vencido no se envia (FR-015) ─────────────────────────────────────────────

    @Test
    @DisplayName("con momento: sirve hasta un segundo antes y ya no sirve desde el momento")
    void vigenciaConMomento() {
        AvisoDeHabito aviso = inicio(A_LAS_21_EN_LIMA, LIMA);

        assertThat(aviso.yaNoSirve(A_LAS_21_EN_LIMA.minusSeconds(1))).isFalse();
        assertThat(aviso.yaNoSirve(A_LAS_21_EN_LIMA)).isTrue();
        assertThat(aviso.yaNoSirve(A_LAS_21_EN_LIMA.plusSeconds(1800))).isTrue();
    }

    @Test
    @DisplayName("respaldo sin momento: emitido + minutos que faltan, truncado al minuto")
    void vigenciaSinMomento() {
        // Emitido a las 01:50:20 faltando "10 min" (redondeado hacia arriba): el momento real era 02:00:00.
        AvisoDeHabito viejo = new AvisoDeHabito("INICIO", "Meditación nocturna", 10, 10, HABITO,
                Instant.parse("2026-09-06T01:50:20Z"), null, null);

        assertThat(viejo.yaNoSirve(Instant.parse("2026-09-06T01:59:59Z"))).isFalse();
        assertThat(viejo.yaNoSirve(Instant.parse("2026-09-06T02:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("el momento del evento manda sobre el calculado con los minutos")
    void elMomentoManda() {
        // 10 min despues de la emision serian las 02:00, pero el momento real del habito es 02:10.
        AvisoDeHabito aviso = new AvisoDeHabito("INICIO", "Meditación nocturna", 10, 10, HABITO, EMITIDO,
                Instant.parse("2026-09-06T02:10:00Z"), LIMA);

        assertThat(aviso.yaNoSirve(Instant.parse("2026-09-06T02:05:00Z"))).isFalse();
    }
}
