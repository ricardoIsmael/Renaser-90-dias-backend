package com.renaser.os.onboarding.domain.model.terminos;

import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cuándo una persona debe aceptar unos términos y qué publicar exige (spec 027, FR-007, FR-015 a FR-017): lógica pura. */
class ReglasDeTerminosTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");
    private static final ContenidoDeTerminos TEXTO = new ContenidoDeTerminos("Términos", "Intro", List.of("Uno", "Dos"), "Acepto");

    private static VersionDeTerminos publicada(int numero, boolean pideNueva, String nota) {
        return VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, TEXTO, numero == 1 ? null : numero - 1, AHORA)
                .publicada(numero, nota, pideNueva, null, AHORA);
    }

    @Test
    @DisplayName("sin versión vigente (la formación no publicó términos) no se pide nada")
    void sinVigenteNoPideNada() {
        assertThat(ReglasDeTerminos.quePide(Optional.empty(), Optional.empty(), true, List.of())).isEqualTo(QuePideLaAceptacion.NADA);
    }

    @Test
    @DisplayName("quien nunca aceptó nada está en su primer ingreso: lo lleva el onboarding y no bloquea")
    void primeraVez() {
        var v1 = publicada(1, false, null);
        var pide = ReglasDeTerminos.quePide(Optional.of(v1), Optional.empty(), false, List.of(v1));
        assertThat(pide).isEqualTo(QuePideLaAceptacion.PRIMERA_VEZ);
        assertThat(pide.bloquea()).isFalse();
    }

    @Test
    @DisplayName("quien ya aceptó los de otra formación y entra a esta debe aceptar los de la nueva, y bloquea (Q4-A, Q11-A)")
    void primeraEnLaFormacion() {
        var v1 = publicada(1, false, null);
        var pide = ReglasDeTerminos.quePide(Optional.of(v1), Optional.empty(), true, List.of(v1));
        assertThat(pide).isEqualTo(QuePideLaAceptacion.PRIMERA_EN_LA_FORMACION);
        assertThat(pide.bloquea()).isTrue();
    }

    @Test
    @DisplayName("una versión nueva que NO pide aceptar de nuevo no interrumpe a nadie (FR-017)")
    void sinPedirNoInterrumpe() {
        var v1 = publicada(1, false, null);
        var v2 = publicada(2, false, "Cambió una coma");
        assertThat(ReglasDeTerminos.quePide(Optional.of(v2), Optional.of(1), true, List.of(v1, v2))).isEqualTo(QuePideLaAceptacion.NADA);
    }

    @Test
    @DisplayName("una versión que pide aceptar de nuevo bloquea a quien aceptó una anterior; quien aceptó la vigente no")
    void pidiendoBloquea() {
        var v1 = publicada(1, false, null);
        var v2 = publicada(2, true, "Cambió el reembolso");
        assertThat(ReglasDeTerminos.quePide(Optional.of(v2), Optional.of(1), true, List.of(v1, v2))).isEqualTo(QuePideLaAceptacion.NUEVA_VERSION);
        assertThat(ReglasDeTerminos.quePide(Optional.of(v2), Optional.of(2), true, List.of(v1, v2))).isEqualTo(QuePideLaAceptacion.NADA);
    }

    @Test
    @DisplayName("si una versión intermedia pidió aceptar de nuevo y la vigente no, quien se quedó en la anterior igual debe aceptar")
    void laIntermediaPesa() {
        var v1 = publicada(1, false, null);
        var v2 = publicada(2, true, "Cambió el reembolso");
        var v3 = publicada(3, false, "Una coma");
        assertThat(ReglasDeTerminos.quePide(Optional.of(v3), Optional.of(1), true, List.of(v1, v2, v3))).isEqualTo(QuePideLaAceptacion.NUEVA_VERSION);
        assertThat(ReglasDeTerminos.quePide(Optional.of(v3), Optional.of(2), true, List.of(v1, v2, v3))).isEqualTo(QuePideLaAceptacion.NADA);
    }

    @Test
    @DisplayName("la nota que se muestra es la de la vigente, o la de la última que pidió aceptar")
    void laNota() {
        var v2 = publicada(2, true, "Cambió el reembolso");
        var v3 = publicada(3, false, null);
        assertThat(ReglasDeTerminos.notaDelCambio(Optional.of(v2), List.of(v2), 1)).contains("Cambió el reembolso");
        assertThat(ReglasDeTerminos.notaDelCambio(Optional.of(v3), List.of(v2, v3), 1)).contains("Cambió el reembolso");
        assertThat(ReglasDeTerminos.notaDelCambio(Optional.empty(), List.of(), 0)).isEmpty();
    }

    @Test
    @DisplayName("una versión publicada no se vuelve a publicar")
    void noSePublicaDosVeces() {
        var v1 = publicada(1, false, null);
        assertThatThrownBy(() -> v1.publicada(2, null, false, null, AHORA)).isInstanceOf(IllegalStateException.class);
        assertThat(v1.estaPublicada()).isTrue();
        assertThat(v1.numero()).isEqualTo(1);
    }

    @Test
    @DisplayName("la nota del cambio puede tener hasta 500 caracteres")
    void laNotaTieneTope() {
        var borrador = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, TEXTO, null, AHORA);
        assertThatThrownBy(() -> borrador.publicada(1, "x".repeat(501), false, null, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(borrador.publicada(1, "  ", false, null, AHORA).nota()).as("una nota en blanco es ninguna nota").isNull();
    }
}
