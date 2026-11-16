package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuándo se avisa que los WhatsApp no salen por falta de saldo (spec 054, FR-001 a FR-003). */
class AvisoDeSaldoDeWhatsAppTest {

    private static final Instant T0 = Instant.parse("2026-11-14T15:20:00Z");

    private static Instant mas(int horas) {
        return T0.plusSeconds(horas * 3600L);
    }

    // spec 054 FR-001
    @Test
    @DisplayName("reconoce el texto de YCloud sin distinguir mayúsculas y no confunde otros motivos ni los vacíos")
    void elPatron() {
        assertThat(AvisoDeSaldoDeWhatsApp.esFallaPorSaldo("The account balance is insufficient.")).isTrue();
        assertThat(AvisoDeSaldoDeWhatsApp.esFallaPorSaldo("the ACCOUNT BALANCE IS INSUFFICIENT")).isTrue();
        assertThat(AvisoDeSaldoDeWhatsApp.esFallaPorSaldo("La plantilla no está aprobada.")).isFalse();
        assertThat(AvisoDeSaldoDeWhatsApp.esFallaPorSaldo("Sin WhatsApp")).isFalse();
        assertThat(AvisoDeSaldoDeWhatsApp.esFallaPorSaldo(null)).isFalse();
    }

    // spec 054 FR-001
    @Test
    @DisplayName("sin fallas por saldo no hay aviso, haya o no envíos buenos")
    void sinFallasNoHayAviso() {
        assertThat(AvisoDeSaldoDeWhatsApp.evaluar(List.of(), Optional.empty()).avisar()).isFalse();
        assertThat(AvisoDeSaldoDeWhatsApp.evaluar(List.of(), Optional.of(T0)).avisar()).isFalse();
    }

    // spec 054 FR-001, FR-003
    @Test
    @DisplayName("con fallas y ningún envío bueno avisa, con cuántas y desde la primera")
    void conFallasAvisa() {
        AvisoDeSaldoDeWhatsApp aviso = AvisoDeSaldoDeWhatsApp.evaluar(List.of(mas(2), T0, mas(5)), Optional.empty());

        assertThat(aviso.avisar()).isTrue();
        assertThat(aviso.fallaron()).isEqualTo(3);
        assertThat(aviso.desde()).isEqualTo(T0);
    }

    // spec 054 FR-002
    @Test
    @DisplayName("si después de la última falla salió uno bien, el aviso desaparece solo")
    void unEnvioBuenoDespuesApagaElAviso() {
        assertThat(AvisoDeSaldoDeWhatsApp.evaluar(List.of(T0, mas(1)), Optional.of(mas(2)))).isEqualTo(AvisoDeSaldoDeWhatsApp.NINGUNO);
    }

    // spec 054 FR-002, FR-003
    @Test
    @DisplayName("un envío bueno anterior a la última falla no apaga el aviso y solo se cuenta la racha posterior")
    void unEnvioBuenoAnteriorNoApaga() {
        AvisoDeSaldoDeWhatsApp aviso = AvisoDeSaldoDeWhatsApp.evaluar(List.of(T0, mas(3), mas(4)), Optional.of(mas(1)));

        assertThat(aviso.avisar()).isTrue();
        assertThat(aviso.fallaron()).isEqualTo(2);
        assertThat(aviso.desde()).isEqualTo(mas(3));
    }
}
