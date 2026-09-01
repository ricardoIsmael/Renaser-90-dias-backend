package com.renaser.os.habits.domain.model.espiritu;

import com.renaser.os.habits.domain.model.espiritu.RegistroEspirituId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spirit-audio/service.ts traducido — ver docs/MODULO_HABITS.md. */
class RegistroEspirituTest {

    private static final Instant DESBLOQUEO = Instant.parse("2026-08-24T07:00:00Z");
    private static final Instant LIMITE = Instant.parse("2026-08-24T12:00:00Z");

    private static RegistroEspiritu nuevo() {
        return RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), 1,
                DESBLOQUEO, LIMITE, DESBLOQUEO);
    }

    @Test
    void entregaATiempoQuedaEntregado() {
        RegistroEspiritu registro = nuevo();
        boolean aTiempo = registro.entregar("resumen", LIMITE.minus(Duration.ofMinutes(1)));

        assertThat(aTiempo).isTrue();
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
        assertThat(registro.resumenTexto()).isEqualTo("resumen");
    }

    @Test
    void entregaJustoEnElLimiteCuentaATiempo() {
        RegistroEspiritu registro = nuevo();
        boolean aTiempo = registro.entregar("resumen", LIMITE);

        assertThat(aTiempo).isTrue();
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
    }

    @Test
    void entregaFueraDePlazoQuedaPendienteYNoLanza() {
        RegistroEspiritu registro = nuevo();
        Instant tarde = LIMITE.plus(Duration.ofMinutes(1));

        boolean aTiempo = registro.entregar("llegue tarde", tarde);

        assertThat(aTiempo).isFalse();
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.PENDIENTE);
        assertThat(registro.resumenTexto()).isEqualTo("llegue tarde");
        assertThat(registro.entregadoEn()).isEqualTo(tarde);
    }

    @Test
    void entregaTardiaSePuedeSobreescribirMientrasSigaPendiente() {
        RegistroEspiritu registro = nuevo();
        registro.entregar("primer intento tarde", LIMITE.plus(Duration.ofMinutes(1)));

        boolean segundaVez = registro.entregar("segundo intento", LIMITE.plus(Duration.ofMinutes(30)));

        assertThat(segundaVez).isFalse();
        assertThat(registro.resumenTexto()).isEqualTo("segundo intento");
    }

    @Test
    void entregarUnRegistroYaEntregadoLanza() {
        RegistroEspiritu registro = nuevo();
        registro.entregar("resumen", DESBLOQUEO);

        assertThatThrownBy(() -> registro.entregar("otro", DESBLOQUEO)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarPerdidoEsIdempotenteYNoTocaUnoYaEntregado() {
        RegistroEspiritu registro = nuevo();
        registro.entregar("resumen", DESBLOQUEO);

        registro.marcarPerdido(LIMITE.plus(Duration.ofDays(1)));

        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
    }

    @Test
    void marcarPerdidoPasaUnPendienteAPerdido() {
        RegistroEspiritu registro = nuevo();

        registro.marcarPerdido(LIMITE.plus(Duration.ofDays(1)));

        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.PERDIDO);
    }

    @Test
    void diaFueraDeRangoRechazado() {
        UserId participante = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), participante, 0,
                DESBLOQUEO, LIMITE, DESBLOQUEO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), participante,
                91, DESBLOQUEO, LIMITE, DESBLOQUEO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
