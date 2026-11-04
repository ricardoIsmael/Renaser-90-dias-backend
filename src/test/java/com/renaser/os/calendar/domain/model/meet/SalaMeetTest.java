package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalaMeetTest {

    private static final Instant T0 = Instant.parse("2026-11-05T15:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-05T15:05:00Z");

    private static SalaMeet nueva() {
        return SalaMeet.nueva(UUID.randomUUID(), EventoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 6), "abc123", AccesoSalaMeet.ABIERTA, T0);
    }

    // spec 040 FR-012
    @Test
    @DisplayName("FR-012: una sala nueva nace PENDIENTE, sin enlace, lista para el primer intento y con el acceso elegido")
    void nacePendiente() {
        SalaMeet sala = nueva();

        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE);
        assertThat(sala.enlace()).isNull();
        assertThat(sala.accesoElegido()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(sala.accesoConfirmado()).isNull();
        assertThat(sala.tocaReintentar(T0)).isTrue();
    }

    // spec 040 FR-003, FR-012
    @Test
    @DisplayName("FR-003: completar guarda el enlace, queda CREADO y olvida los fallos")
    void completarGuardaElEnlace() {
        SalaMeet sala = nueva();
        sala.fallarAlCrear("Google no respondió", T0, T1);

        sala.completarCon("https://meet.google.com/abc-defg-hij", T1);

        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(sala.enlace()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(sala.intentos()).isZero();
        assertThat(sala.ultimoError()).isNull();
        assertThat(sala.proximoIntento()).isNull();
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: cada fallo suma un intento y guarda un motivo corto y sin saltos de línea")
    void fallarSumaIntentosYLimpiaElMotivo() {
        SalaMeet sala = nueva();
        String largo = "Google dijo\n" + "x".repeat(500);

        sala.fallarAlCrear(largo, T0, T1);

        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        assertThat(sala.intentos()).isEqualTo(1);
        assertThat(sala.ultimoError()).hasSize(SalaMeet.LARGO_MAXIMO_DEL_ERROR).doesNotContain("\n");
        assertThat(sala.tocaReintentar(T0)).as("todavía no es su turno").isFalse();
        assertThat(sala.tocaReintentar(T1)).isTrue();
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: sin próximo intento (se rindió) no vuelve a tocar nunca, ni agotados los 8")
    void sinProximoIntentoNoTocaMas() {
        SalaMeet sala = nueva();
        sala.fallarAlCrear("x", T0, null);

        assertThat(sala.tocaReintentar(T1.plusSeconds(86_400))).isFalse();
    }

    // spec 040 FR-005, FR-008
    @Test
    @DisplayName("FR-005: editar el evento pide sincronizar solo una sala que ya existe; una sala sin crear sigue como estaba")
    void pedirSincronizarSoloEnUnaSalaCreada() {
        SalaMeet sinCrear = nueva();
        sinCrear.pedirSincronizar(T1);
        assertThat(sinCrear.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE);

        SalaMeet creada = nueva();
        creada.completarCon("https://meet.google.com/abc-defg-hij", T0);
        creada.pedirSincronizar(T1);
        assertThat(creada.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR);
        assertThat(creada.enlace()).as("el enlace no cambia").isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(creada.tocaReintentar(T1)).isTrue();

        creada.sincronizada(T1);
        assertThat(creada.estado()).isEqualTo(EstadoSalaMeet.CREADO);
    }

    // spec 040 FR-012
    @Test
    @DisplayName("FR-012: borrada deja una lápida sin enlace; reactivarla pide una sala nueva con el mismo id de Google")
    void lapidaYReactivacion() {
        SalaMeet sala = nueva();
        sala.completarCon("https://meet.google.com/abc-defg-hij", T0);

        sala.marcarBorrada(T1);
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.BORRADO);
        assertThat(sala.enlace()).isNull();
        assertThat(sala.tocaReintentar(T1)).isFalse();

        sala.reactivar(AccesoSalaMeet.CON_PERMISO, T1);
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE);
        assertThat(sala.idGoogle()).isEqualTo("abc123");
        assertThat(sala.accesoElegido()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: lo elegido y lo que Google confirmó pueden no coincidir: la sala sigue CREADO pero con el acceso pendiente")
    void accesoElegidoYConfirmado() {
        SalaMeet sala = nueva();
        sala.completarCon("https://meet.google.com/abc-defg-hij", T0);

        assertThat(sala.accesoPendiente()).as("nunca se confirmó").isTrue();

        sala.registrarAccesoConfirmado(AccesoSalaMeet.CON_PERMISO, T0);
        sala.fallarElAcceso("Google no dejó la sala como se eligió.", T0, T1);
        assertThat(sala.accesoPendiente()).isTrue();
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(sala.tocaReintentar(T1)).isTrue();

        sala.registrarAccesoConfirmado(AccesoSalaMeet.ABIERTA, T1);
        assertThat(sala.accesoPendiente()).isFalse();
        assertThat(sala.ultimoError()).isNull();
        assertThat(sala.proximoIntento()).isNull();
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: elegir otro acceso la deja lista para aplicarlo; elegir el mismo no cambia nada")
    void elegirOtroAcceso() {
        SalaMeet sala = nueva();
        sala.completarCon("https://meet.google.com/abc-defg-hij", T0);
        sala.registrarAccesoConfirmado(AccesoSalaMeet.ABIERTA, T0);

        sala.elegirAcceso(AccesoSalaMeet.ABIERTA, T1);
        assertThat(sala.accesoPendiente()).isFalse();

        sala.elegirAcceso(AccesoSalaMeet.CON_PERMISO, T1);
        assertThat(sala.accesoPendiente()).isTrue();
        assertThat(sala.tocaReintentar(T1)).isTrue();
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: reencolar (tras reconectar la cuenta) devuelve el turno a lo que se había rendido, pero no toca lo que está bien")
    void reencolar() {
        SalaMeet rendida = nueva();
        rendida.fallarAlCrear("x", T0, null);
        rendida.reencolar(T1);
        assertThat(rendida.tocaReintentar(T1)).isTrue();
        assertThat(rendida.intentos()).isZero();

        SalaMeet bien = nueva();
        bien.completarCon("https://meet.google.com/abc-defg-hij", T0);
        bien.registrarAccesoConfirmado(AccesoSalaMeet.ABIERTA, T0);
        bien.reencolar(T1);
        assertThat(bien.proximoIntento()).isNull();
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: un evento de un solo día movido a otra fecha conserva su sala; solo cambia la fecha")
    void moverLaFecha() {
        SalaMeet sala = nueva();
        sala.completarCon("https://meet.google.com/abc-defg-hij", T0);

        sala.moverA(LocalDate.of(2026, 11, 9), T1);

        assertThat(sala.fechaOcurrencia()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(sala.enlace()).isEqualTo("https://meet.google.com/abc-defg-hij");
    }
}
