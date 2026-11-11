package com.renaser.os.shared.application;

import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioNuevo;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioRegistrado;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La entrega de lo hecho sin red (spec 048, fase 3): candado, repetición, aplicado y no aplicado, y que siempre queda su fila.
 * El puerto es un doble; contra Postgres de verdad lo prueba {@code EnviosSinRedGeneralIT}.
 */
class EntregaSinRedTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");

    private final EnviosSinRedPort envios = mock(EnviosSinRedPort.class);
    private final EntregaSinRed entrega = new EntregaSinRed(envios, FixedClock.at(Instant.parse("2026-11-10T13:00:00Z")));
    private final Envio envio = new Envio(ANA, new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA), "CIERRE_DEL_DIA", "cierre:2026-11-09");

    @BeforeEach
    void dentroDeUnaTransaccion() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(envios.buscar(ENVIO, ANA)).thenReturn(Optional.empty());
    }

    @AfterEach
    void fueraDeLaTransaccion() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("la primera vez toma el candado, aplica y deja su fila APLICADO con la hora real y el origen")
    void aplicaYRegistra() { // spec 048 FR-008 FR-019
        String respuesta = entrega.entregar(envio, () -> Aplicado.de("hecho"), previo -> "releído");

        assertThat(respuesta).isEqualTo("hecho");
        var orden = inOrder(envios);
        orden.verify(envios).tomarElCandado(ENVIO);
        orden.verify(envios).buscar(ENVIO, ANA);
        ArgumentCaptor<EnvioNuevo> fila = ArgumentCaptor.forClass(EnvioNuevo.class);
        orden.verify(envios).registrar(fila.capture());
        assertThat(fila.getValue().noAplicado()).isNull();
        assertThat(fila.getValue().tipo()).isEqualTo("CIERRE_DEL_DIA");
        assertThat(fila.getValue().recurso()).isEqualTo("cierre:2026-11-09");
        assertThat(fila.getValue().hechoEn()).isEqualTo(HECHO);
        assertThat(fila.getValue().origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    @Test
    @DisplayName("un envío que ya llegó y se aplicó NO vuelve a aplicar: responde releyendo lo que quedó")
    void repeticionDeUnAplicado() { // spec 048 FR-008
        when(envios.buscar(ENVIO, ANA)).thenReturn(Optional.of(new EnvioRegistrado(null, "HORA_FUTURA")));
        AtomicInteger aplicaciones = new AtomicInteger();

        String respuesta = entrega.entregar(envio, () -> {
            aplicaciones.incrementAndGet();
            return Aplicado.de("otra vez");
        }, previo -> "releído con " + previo.motivoSinPuntos());

        assertThat(respuesta).isEqualTo("releído con HORA_FUTURA");
        assertThat(aplicaciones).hasValue(0);
        verify(envios, never()).registrar(org.mockito.ArgumentMatchers.any(EnvioNuevo.class));
    }

    @Test
    @DisplayName("lo que no se aplica queda registrado NO_APLICADO con su código y su mensaje, y se relanza el mismo 409")
    void noAplicadoQuedaRegistrado() { // spec 048 FR-016 FR-019
        assertThatThrownBy(() -> entrega.entregar(envio, () -> {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }, previo -> "no se llama")).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        ArgumentCaptor<EnvioNuevo> fila = ArgumentCaptor.forClass(EnvioNuevo.class);
        verify(envios).registrar(fila.capture());
        assertThat(fila.getValue().noAplicado()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        assertThat(fila.getValue().mensaje()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR.mensaje());
    }

    @Test
    @DisplayName("repetir un envío que no se aplicó devuelve el mismo 409 con el mismo código y mensaje, sin aplicar ni escribir")
    void repeticionDeUnNoAplicado() { // spec 048 FR-008 FR-019
        when(envios.buscar(ENVIO, ANA)).thenReturn(Optional.of(
                new EnvioRegistrado(null, null, CodigoNoAplicado.YA_ESTABA_HECHO, "mensaje guardado")));

        assertThatThrownBy(() -> entrega.entregar(envio, () -> Aplicado.de("no"), previo -> "no"))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO);
                    assertThat(e.getMessage()).isEqualTo("mensaje guardado");
                });
        verify(envios, never()).registrar(org.mockito.ArgumentMatchers.any(EnvioNuevo.class));
    }

    @Test
    @DisplayName("otro error (un 400, un 403) se propaga sin dejar fila: un reintento se vuelve a evaluar")
    void otroErrorNoDejaFila() { // spec 048 FR-008
        assertThatThrownBy(() -> entrega.entregar(envio, () -> {
            throw new IllegalArgumentException("datos mal hechos");
        }, previo -> "no")).isInstanceOf(IllegalArgumentException.class);

        verify(envios, never()).registrar(org.mockito.ArgumentMatchers.any(EnvioNuevo.class));
    }

    @Test
    @DisplayName("sin una transacción en curso no entrega: el candado y la fila tienen que ir con lo aplicado")
    void exigeTransaccion() { // spec 048 FR-008
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> entrega.entregar(envio, () -> Aplicado.de("x"), previo -> "x"))
                .isInstanceOf(IllegalStateException.class);
        verify(envios, never()).tomarElCandado(org.mockito.ArgumentMatchers.any());
    }
}
