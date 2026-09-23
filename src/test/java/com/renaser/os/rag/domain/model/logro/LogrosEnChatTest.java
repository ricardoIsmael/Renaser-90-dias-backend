package com.renaser.os.rag.domain.model.logro;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogrosEnChatTest {

    private static final Map<TipoLogro, String> TEXTOS = Map.of(
            TipoLogro.RACHA_SIN_CELULAR, "  ¡Lo lograste! Completaste tu día sin celular.  ",
            TipoLogro.ROCA_COMPLETADA, "¡Roca completada!");

    @Test
    void apagadoNoCelebraNada() {
        var apagado = new LogrosEnChat(false, Set.of(TipoLogro.values()), TEXTOS);

        assertThat(apagado.aplicaA(TipoLogro.RACHA_SIN_CELULAR)).isFalse();
        assertThat(apagado.redactar(TipoLogro.RACHA_SIN_CELULAR)).isEmpty();
        assertThat(LogrosEnChat.apagados().aplicaA(TipoLogro.ROCA_COMPLETADA)).isFalse();
    }

    @Test
    void redactaLaPlantillaDelTipoSinEspaciosDeMas() {
        var prendido = new LogrosEnChat(true, Set.of(TipoLogro.values()), TEXTOS);

        assertThat(prendido.redactar(TipoLogro.RACHA_SIN_CELULAR))
                .contains("¡Lo lograste! Completaste tu día sin celular.");
        assertThat(prendido.redactar(TipoLogro.ROCA_COMPLETADA)).contains("¡Roca completada!");
    }

    @Test
    void unTipoNoElegidoNoSeCelebra() {
        var soloRacha = new LogrosEnChat(true, Set.of(TipoLogro.RACHA_SIN_CELULAR), TEXTOS);

        assertThat(soloRacha.aplicaA(TipoLogro.ROCA_COMPLETADA)).isFalse();
        assertThat(soloRacha.redactar(TipoLogro.ROCA_COMPLETADA)).isEmpty();
    }

    @Test
    void unaPlantillaVaciaApagaEseTipo() {
        var sinTexto = new LogrosEnChat(true, Set.of(TipoLogro.values()),
                Map.of(TipoLogro.RACHA_SIN_CELULAR, "   ", TipoLogro.ROCA_COMPLETADA, ""));

        assertThat(sinTexto.aplicaA(TipoLogro.RACHA_SIN_CELULAR)).isFalse();
        assertThat(sinTexto.aplicaA(TipoLogro.ROCA_COMPLETADA)).isFalse();
        assertThat(sinTexto.aplicaA(null)).isFalse();
    }
}
