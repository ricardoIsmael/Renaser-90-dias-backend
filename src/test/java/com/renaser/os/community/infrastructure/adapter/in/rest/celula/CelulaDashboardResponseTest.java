package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.application.ports.in.celula.ConsultarDashboardCelulasUseCase.CelulaConCohorte;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El tablero de grupos dice el cupo de cada uno (spec 056, FR-008): el asistente de «Crear grupo» apaga los llenos. */
class CelulaDashboardResponseTest {

    private static final Instant AHORA = Instant.parse("2026-11-18T15:00:00Z");

    private static CelulaConCohorte item(TipoCelula tipo, Integer cupoMaximo) {
        CohorteId cohorteId = CohorteId.of(UUID.randomUUID());
        Celula celula = Celula.rehydrate(CelulaId.of(UUID.randomUUID()), "Fénix", null, cohorteId, null, null, AHORA, AHORA, tipo, null, null,
                Formacion.ESCUELA);
        Cohorte cohorte = Cohorte.rehydrate(cohorteId, "Cohorte", LocalDate.of(2026, 11, 1), null, EstadoCohorte.ACTIVA, AHORA, AHORA);
        return new CelulaConCohorte(celula, 3, new PerfilBasico(UserId.of(UUID.randomUUID()), "Mentor", null), cohorte, cupoMaximo);
    }

    // spec 056 FR-008
    @Test
    @DisplayName("spec 056 FR-008: el tablero trae el cupo de cada grupo en capacity")
    void elTableroTraeElCupoDeCadaGrupo() {
        assertThat(CelulaDashboardResponse.from(item(TipoCelula.REGULAR, 12)).capacity()).isEqualTo(12);
    }

    // spec 056 FR-008
    @Test
    @DisplayName("spec 056 FR-008: un grupo de recepción no tiene tope: capacity viene en null, no en 0")
    void recepcionNoTieneTope() {
        assertThat(CelulaDashboardResponse.from(item(TipoCelula.RECEPCION, null)).capacity()).isNull();
    }
}
