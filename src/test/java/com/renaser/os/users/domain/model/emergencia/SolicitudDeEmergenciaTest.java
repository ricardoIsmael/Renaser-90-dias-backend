package com.renaser.os.users.domain.model.emergencia;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-244: las reglas del pedido de emergencia que se evalúan con sus propios datos. */
class SolicitudDeEmergenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));
    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    private SolicitudDeEmergencia pedir(String texto, Integer diaPedido, int diaActual) {
        return SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, texto, diaPedido, diaActual, CLOCK);
    }

    @Test
    @DisplayName("nace abierta, con el texto sin espacios de más, el día pedido y el día en que estaba")
    void naceAbierta() {
        SolicitudDeEmergencia s = pedir("  Me operaron de urgencia  ", 12, 20);

        assertThat(s.abierta()).isTrue();
        assertThat(s.queOcurrio()).isEqualTo("Me operaron de urgencia");
        assertThat(s.diaPedido()).isEqualTo(12);
        assertThat(s.diaAlPedir()).isEqualTo(20);
        assertThat(s.creadaEn()).isEqualTo(CLOCK.now());
        assertThat(s.resueltaEn()).isNull();
    }

    @Test
    @DisplayName("se puede pedir del 1 al día actual, incluidos los dos extremos")
    void rangoValido() {
        assertThat(pedir("Accidente", 1, 20).diaPedido()).isEqualTo(1);
        assertThat(pedir("Accidente", 20, 20).diaPedido()).isEqualTo(20);
    }

    @Test
    @DisplayName("fuera de 1..día actual es un 400 que dice el rango")
    void fueraDeRango() {
        assertThatThrownBy(() -> pedir("Accidente", 0, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige un día entre 1 y 20.");
        assertThatThrownBy(() -> pedir("Accidente", 21, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige un día entre 1 y 20.");
    }

    @Test
    @DisplayName("en el día 90 el tope es 89: el 90 no se fija a mano (D-194)")
    void enElDia90ElTopeEs89() {
        assertThat(SolicitudDeEmergencia.diaMaximoPedible(90)).isEqualTo(89);
        assertThatThrownBy(() -> pedir("Accidente", 90, 90)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige un día entre 1 y 89.");
    }

    /** Respuesta del dueño (02/10): disponible desde el Día 0. Contra la versión anterior era un 409. */
    @Test
    @DisplayName("en el Día 0 se pide ayuda sin día; con un día es 400")
    void enElDiaCeroSePideSinDia() {
        assertThat(SolicitudDeEmergencia.diaMaximoPedible(0)).isZero();
        SolicitudDeEmergencia s = pedir("Me enfermé antes de empezar", null, 0);
        assertThat(s.abierta()).isTrue();
        assertThat(s.pideUnDia()).isFalse();
        assertThat(s.diaAlPedir()).isZero();
        assertThatThrownBy(() -> pedir("Accidente", 1, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(SolicitudDeEmergencia.SIN_DIA_EN_EL_DIA_CERO);
    }

    @Test
    @DisplayName("desde el Día 1 el día es obligatorio")
    void desdeElDiaUnoElDiaEsObligatorio() {
        assertThatThrownBy(() -> pedir("Accidente", null, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige un día entre 1 y 5.");
    }

    @Test
    @DisplayName("qué pasó es obligatorio y tiene tope de 280")
    void textoObligatorioYCorto() {
        assertThatThrownBy(() -> pedir("   ", 5, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pedir(null, 5, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pedir("a".repeat(280), 5, 20).queOcurrio()).hasSize(280);
        assertThatThrownBy(() -> pedir("a".repeat(281), 5, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escríbelo en 280 caracteres o menos.");
    }

    @Test
    @DisplayName("cambiar el día la resuelve con quién y a qué día; no se cierra dos veces")
    void resolverConCambioDeDia() {
        SolicitudDeEmergencia s = pedir("Accidente", 12, 20);
        UserId admin = UserId.of(UUID.randomUUID());

        s.resolverConCambioDeDia(admin, 13, CLOCK);

        assertThat(s.abierta()).isFalse();
        assertThat(s.estado()).isEqualTo(EstadoDeEmergencia.RESUELTA);
        assertThat(s.resueltaPor()).isEqualTo(admin);
        assertThat(s.diaAplicado()).isEqualTo(13);
        assertThat(s.resueltaEn()).isEqualTo(CLOCK.now());
        assertThatThrownBy(() -> s.cerrarSinCambio(admin, CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cerrarla sin cambio deja el día aplicado vacío")
    void cerrarSinCambio() {
        SolicitudDeEmergencia s = pedir("Accidente", 12, 20);

        s.cerrarSinCambio(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(s.estado()).isEqualTo(EstadoDeEmergencia.RESUELTA);
        assertThat(s.diaAplicado()).isNull();
    }
}
