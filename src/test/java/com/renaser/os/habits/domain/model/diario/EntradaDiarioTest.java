package com.renaser.os.habits.domain.model.diario;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRN-18 (e2e del 2026-09-27): la bitacora aceptaba cualquier largo; con 1 MB respondia 200 y lo
 * guardaba entero. El tope vive en el agregado, asi que vale igual para el {@code PUT} de la app y
 * para la que guarda el acompanante.
 */
class EntradaDiarioTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T19:22:46Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 27);
    private static final UserId PERSONA = UserId.of(UUID.randomUUID());

    private static EntradaDiario escribir(String texto) {
        return EntradaDiario.escribir(EntradaDiarioId.of(UUID.randomUUID()), PERSONA, HOY,
                TipoEntradaDiario.BITACORA_NOCTURNA, texto, AHORA);
    }

    @Test
    @DisplayName("TRN-18: un caracter mas que el tope se rechaza, con el tope y el largo que llego")
    void unCaracterMasQueElTopeSeRechaza() {
        assertThatThrownBy(() -> escribir("x".repeat(4_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4000")
                .hasMessageContaining("4001");
    }

    @Test
    @DisplayName("TRN-18: justo en el tope se guarda")
    void justoEnElTopeSeGuarda() {
        assertThat(escribir("x".repeat(4_000)).contenidoTexto()).hasSize(4_000);
    }

    @Test
    @DisplayName("TRN-18: el tope cuenta caracteres como los cuenta una persona: un emoji es uno")
    void unEmojiCuentaComoUnCaracter() {
        String cuatroMilEmojis = "🌅".repeat(4_000);

        assertThat(escribir(cuatroMilEmojis).contenidoTexto()).isEqualTo(cuatroMilEmojis);
    }

    @Test
    @DisplayName("TRN-18: reescribir la de hoy tampoco se salta el tope, y la entrada queda como estaba")
    void reescribirTampocoSeSaltaElTope() {
        EntradaDiario entrada = escribir("primer intento");

        assertThatThrownBy(() -> entrada.actualizarTexto("x".repeat(4_001), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entrada.contenidoTexto()).isEqualTo("primer intento");
    }

    @Test
    @DisplayName("sin texto sigue valiendo: una bitacora puede ser solo audio")
    void sinTextoSigueValiendo() {
        assertThat(escribir(null).contenidoTexto()).isNull();
    }
}
