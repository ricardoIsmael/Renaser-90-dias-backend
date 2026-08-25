package com.renaser.os.onboarding.domain.model.respuesta;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespuestaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId newUsuarioId() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("TEXTO: crea con valorTexto, rechaza si ademas viene otro valor")
    void textoRequiereSoloValorTexto() {
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.TEXTO, newUsuarioId(), 1, "hola", null, null, null,
                null, null, CLOCK);
        assertThat(r.valorTexto()).isEqualTo("hola");
        assertThat(r.valorNumero()).isNull();

        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.TEXTO, newUsuarioId(), 1, "hola",
                BigDecimal.TEN, null, null, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NUMERO: requiere valorNumero, rechaza valorTexto en su lugar")
    void numeroRequiereValorNumero() {
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.NUMERO, newUsuarioId(), 2, null, BigDecimal.valueOf(42),
                null, null, null, null, CLOCK);
        assertThat(r.valorNumero()).isEqualByComparingTo("42");

        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.NUMERO, newUsuarioId(), 2, "42", null, null,
                null, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ESCALA: acepta 1..10, rechaza fuera de rango")
    void escalaValidaRango() {
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.ESCALA, newUsuarioId(), 3, null, null, null, (short) 7,
                null, null, CLOCK);
        assertThat(r.valorEscala()).isEqualTo((short) 7);

        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.ESCALA, newUsuarioId(), 3, null, null, null,
                (short) 11, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.ESCALA, newUsuarioId(), 3, null, null, null,
                (short) 0, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CASILLA=true fija aceptadaEn; CASILLA=false lo deja null")
    void casillaFijaAceptadaEnSoloSiEsTrue() {
        Respuesta aceptada = Respuesta.crear(TipoPreguntaOnboarding.CASILLA, newUsuarioId(), 4, null, null, true,
                null, null, null, CLOCK);
        assertThat(aceptada.aceptadaEn()).isEqualTo(CLOCK.now());

        Respuesta noAceptada = Respuesta.crear(TipoPreguntaOnboarding.CASILLA, newUsuarioId(), 4, null, null, false,
                null, null, null, CLOCK);
        assertThat(noAceptada.aceptadaEn()).isNull();
    }

    @Test
    @DisplayName("SELECCION_MULTIPLE: requiere valorJson (opaco), no otro slot")
    void seleccionMultipleRequiereValorJson() {
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.SELECCION_MULTIPLE, newUsuarioId(), 5, null, null, null,
                null, "[\"a\",\"b\"]", null, CLOCK);
        assertThat(r.valorJson()).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    @DisplayName("AUDIO/FIRMA/ARCHIVO: requieren mediaId, rechazan cualquier valor tipado")
    void mediaTiposRequierenMediaIdSinValorTipado() {
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.AUDIO, newUsuarioId(), 6, null, null, null, null, null,
                99L, CLOCK);
        assertThat(r.mediaId()).isEqualTo(99L);
        assertThat(r.valorTexto()).isNull();

        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.AUDIO, newUsuarioId(), 6, null, null, null,
                null, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Respuesta.crear(TipoPreguntaOnboarding.FIRMA, newUsuarioId(), 6, "no va", null,
                null, null, null, 99L, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("actualizarValor(): conserva id/usuarioId/preguntaId/respondidaEn -- upsert de dominio, nunca duplica identidad")
    void actualizarValorConservaIdentidad() {
        UserId usuarioId = newUsuarioId();
        Respuesta original = Respuesta.rehydrate(55L, usuarioId, 7, "primero", null, null, null, null, null, null,
                CLOCK.now(), CLOCK.now());

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(120));
        Respuesta actualizada = original.actualizarValor(TipoPreguntaOnboarding.TEXTO, "segundo", null, null, null,
                null, null, masTarde);

        assertThat(actualizada.id()).isEqualTo(55L);
        assertThat(actualizada.usuarioId()).isEqualTo(usuarioId);
        assertThat(actualizada.preguntaId()).isEqualTo(7);
        assertThat(actualizada.respondidaEn()).isEqualTo(CLOCK.now());
        assertThat(actualizada.valorTexto()).isEqualTo("segundo");
        assertThat(actualizada.actualizadoEn()).isEqualTo(masTarde.now());
    }

    @Test
    @DisplayName("rehydrate no re-valida coherencia de tipo (rol del adaptador de persistencia)")
    void rehydrateNoRevalida() {
        Respuesta r = Respuesta.rehydrate(1L, newUsuarioId(), 8, "cualquier cosa", null, null, null, null, null,
                null, CLOCK.now(), CLOCK.now());
        assertThat(r.valorTexto()).isEqualTo("cualquier cosa");
    }
}
