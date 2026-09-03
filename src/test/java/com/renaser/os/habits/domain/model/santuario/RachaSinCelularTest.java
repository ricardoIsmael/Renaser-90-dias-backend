package com.renaser.os.habits.domain.model.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** phoneFree.ts + phoneFreeLadder.ts traducidos 1:1 — ver docs/MODULO_HABITS.md paso 0. */
class RachaSinCelularTest {

    private static final Instant INICIO = Instant.parse("2026-08-24T09:00:00Z");

    private static RachaSinCelular nuevaActiva(int horasObjetivo) {
        return RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                RegistroHabitoId.of(UUID.randomUUID()), horasObjetivo, INICIO);
    }

    @Test
    void horasObjetivoDebeSerUnoDeLosOchoHitos() {
        assertThat(RachaSinCelular.esHorasObjetivoValida(24)).isTrue();
        assertThat(RachaSinCelular.esHorasObjetivoValida(3)).isTrue();
        assertThat(RachaSinCelular.esHorasObjetivoValida(5)).isFalse();
        assertThat(RachaSinCelular.esHorasObjetivoValida(27)).isFalse();
        assertThatThrownBy(() -> nuevaActiva(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("179 min -> hito 0, 180 min -> hito 3 (phoneFreeLadder.ts:41-45)")
    void hitoAlcanzadoBordesExactos() {
        assertThat(RachaSinCelular.hitoAlcanzado(179)).isZero();
        assertThat(RachaSinCelular.hitoAlcanzado(180)).isEqualTo(3);
        assertThat(RachaSinCelular.hitoAlcanzado(1439)).isEqualTo(21);
        assertThat(RachaSinCelular.hitoAlcanzado(1440)).isEqualTo(24);
        assertThat(RachaSinCelular.hitoAlcanzado(5000)).isEqualTo(24); // nunca pasa de 24
    }

    @Test
    void esCicloCompletoSoloEnElHitoDe24() {
        assertThat(RachaSinCelular.esCicloCompleto(1439)).isFalse();
        assertThat(RachaSinCelular.esCicloCompleto(1440)).isTrue();
    }

    @Test
    void minutosTranscurridosTieneTopeEnElCicloCompleto() {
        RachaSinCelular r = nuevaActiva(24);
        int minutos = r.minutosTranscurridos(INICIO.plus(Duration.ofHours(30)));
        assertThat(minutos).isEqualTo(RachaSinCelular.CICLO_COMPLETO_MINUTOS);
    }

    @Test
    void minutosTranscurridosNuncaNegativo() {
        RachaSinCelular r = nuevaActiva(24);
        assertThat(r.minutosTranscurridos(INICIO.minus(Duration.ofMinutes(5)))).isZero();
    }

    @Test
    @DisplayName("cerrar con ciclo completo (>=24h) -> COMPLETADA y devuelve true")
    void cerrarConCicloCompleto() {
        RachaSinCelular r = nuevaActiva(24);
        boolean completo = r.cerrar(INICIO.plus(Duration.ofHours(24)));
        assertThat(completo).isTrue();
        assertThat(r.estado()).isEqualTo(EstadoRacha.COMPLETADA);
        assertThat(r.duracionMinutos()).isEqualTo(1440);
    }

    @Test
    @DisplayName("cerrar con hito parcial (<24h) -> ROTA y devuelve false (no es fallo, es logro parcial)")
    void cerrarConHitoParcial() {
        RachaSinCelular r = nuevaActiva(24);
        boolean completo = r.cerrar(INICIO.plus(Duration.ofHours(9)));
        assertThat(completo).isFalse();
        assertThat(r.estado()).isEqualTo(EstadoRacha.ROTA);
    }

    @Test
    void romperGuardaMotivoYEstadoRota() {
        RachaSinCelular r = nuevaActiva(24);
        r.romper("me olvide el cargador", INICIO.plus(Duration.ofHours(2)));
        assertThat(r.estado()).isEqualTo(EstadoRacha.ROTA);
        assertThat(r.motivoRuptura()).isEqualTo("me olvide el cargador");
    }

    @Test
    void expirarLimitaLaDuracionAlCicloCompleto() {
        RachaSinCelular r = nuevaActiva(24);
        r.expirar(INICIO.plus(Duration.ofHours(40)));
        assertThat(r.estado()).isEqualTo(EstadoRacha.EXPIRADA);
        assertThat(r.duracionMinutos()).isEqualTo(RachaSinCelular.CICLO_COMPLETO_MINUTOS);
    }

    @Test
    void unaRachaYaCerradaNoSePuedeVolverACerrar() {
        RachaSinCelular r = nuevaActiva(24);
        r.cerrar(INICIO.plus(Duration.ofHours(24)));
        assertThatThrownBy(() -> r.cerrar(INICIO.plus(Duration.ofHours(25))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("plazoCierre = inicio + 24h + margen configurado (phoneFreeLadder.ts:143-152)")
    void plazoCierreSumaCicloYMargen() {
        RachaSinCelular r = nuevaActiva(24);
        Instant plazo = r.plazoCierre(3);
        assertThat(plazo).isEqualTo(INICIO.plus(Duration.ofHours(24)).plus(Duration.ofHours(3)));
    }
}
