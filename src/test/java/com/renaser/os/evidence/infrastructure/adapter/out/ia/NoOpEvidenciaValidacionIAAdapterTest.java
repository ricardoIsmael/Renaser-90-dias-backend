package com.renaser.os.evidence.infrastructure.adapter.out.ia;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpEvidenciaValidacionIAAdapterTest {

    @Test
    void siempreDevuelveNoDisponible() {
        FixedClock clock = FixedClock.at(Instant.parse("2026-08-25T12:00:00Z"));
        Evidencia evidencia = Evidencia.registrar(UserId.of(UUID.randomUUID()),
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), TipoEvidencia.TEXTO, null, null, "hecho",
                null, null, null, false, clock.now(), clock);

        ResultadoValidacionIA resultado = new NoOpEvidenciaValidacionIAAdapter().validar(evidencia);

        assertThat(resultado).isEqualTo(ResultadoValidacionIA.NO_DISPONIBLE);
    }
}
