package com.renaser.os.phasecontracts.domain.model.contrato;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContratoFaseTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId newParticipanteId() {
        return UserId.of(UUID.randomUUID());
    }

    private static ContratoFaseId newContratoId() {
        return ContratoFaseId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("firmar() en dia desbloqueado crea el contrato con bucket/ruta deterministicos")
    void firmarCreaContratoConRutaDeterministica() {
        UserId participanteId = newParticipanteId();

        // dia 20 -> Fase II, ya desbloqueada (17)
        ContratoFase contrato = ContratoFase.firmar(newContratoId(), participanteId, 20, CLOCK);

        assertThat(contrato.fase()).isEqualTo(FasePrograma.FASE_2_DESARROLLO);
        assertThat(contrato.bucket()).isEqualTo(ContratoFase.BUCKET_DEFAULT);
        assertThat(contrato.rutaFirma()).isEqualTo("firmas/" + participanteId + "/fase_2.svg");
        assertThat(contrato.firmadoEn()).isEqualTo(CLOCK.now());
        assertThat(contrato.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("firmar() en Fase I rechaza: ese pacto se firma en el onboarding")
    void firmarEnFaseUnoRechaza() {
        UserId participanteId = newParticipanteId();

        assertThatThrownBy(() -> ContratoFase.firmar(newContratoId(), participanteId, 5, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("onboarding");
    }

    @Test
    @DisplayName("firmar() antes del dia de desbloqueo rechaza aunque ya este en esa fase")
    void firmarAntesDeDesbloqueoRechaza() {
        UserId participanteId = newParticipanteId();

        // dia 10: ya es Fase II (arranca dia 8) pero el pacto se desbloquea recien el 17.
        assertThatThrownBy(() -> ContratoFase.firmar(newContratoId(), participanteId, 10, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Todavia no te toca");
    }

    @Test
    @DisplayName("rutaFirma() nombra la fase por numero, no por el texto del enum")
    void rutaFirmaUsaNumeroDeFase() {
        UserId participanteId = newParticipanteId();

        assertThat(ContratoFase.rutaFirma(participanteId, FasePrograma.FASE_3_GUERRERO_ALQUIMISTA))
                .isEqualTo("firmas/" + participanteId + "/fase_3.svg");
        assertThat(ContratoFase.rutaFirma(participanteId, FasePrograma.FASE_4_ASCENSION))
                .isEqualTo("firmas/" + participanteId + "/fase_4.svg");
    }

    @Test
    @DisplayName("rehydrate reconstruye sin volver a validar (rol del adaptador de persistencia)")
    void rehydrateReconstruyeSinValidar() {
        UserId participanteId = newParticipanteId();
        ContratoFaseId id = newContratoId();

        ContratoFase contrato = ContratoFase.rehydrate(id, participanteId, FasePrograma.FASE_2_DESARROLLO,
                "otro-bucket", "otra/ruta.svg", CLOCK.now(), CLOCK.now());

        assertThat(contrato.id()).isEqualTo(id);
        assertThat(contrato.bucket()).isEqualTo("otro-bucket");
    }

    @Test
    @DisplayName("dos contratos con el mismo id son iguales, aunque difieran en otros campos")
    void equalsPorId() {
        ContratoFaseId id = newContratoId();
        UserId participanteId = newParticipanteId();
        ContratoFase a = ContratoFase.rehydrate(id, participanteId, FasePrograma.FASE_2_DESARROLLO,
                ContratoFase.BUCKET_DEFAULT, "a.svg", CLOCK.now(), CLOCK.now());
        ContratoFase b = ContratoFase.rehydrate(id, participanteId, FasePrograma.FASE_3_GUERRERO_ALQUIMISTA,
                ContratoFase.BUCKET_DEFAULT, "b.svg", CLOCK.now(), CLOCK.now());

        assertThat(a).isEqualTo(b);
    }
}
