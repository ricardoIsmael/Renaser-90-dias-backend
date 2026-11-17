package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.mentoring.api.SemanaDelAlumno;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El pilar de cada hábito viaja en la obligación del día: es la {@code categoria_clave} del hábito, tal cual, o null si
 * el hábito no la trae (apps y consultas anteriores).
 */
class PilarEnLaObligacionTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);
    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");

    @Test
    @DisplayName("spec 055 FR-013: la obligación del día trae el pilar (categoria_clave) del hábito y null si no lo tiene")
    void laObligacionTraeElPilarDelHabito() {
        ObligacionHabito conPilar = new ObligacionHabito(UUID.randomUUID(), ANA, DIA, 24, "Agua",
                EstadoObligacion.COMPLETADO, false, false, null, null, null, "CUERPO");
        ObligacionHabito sinPilar = new ObligacionHabito(UUID.randomUUID(), ANA, DIA, 24, "Otro",
                EstadoObligacion.PENDIENTE, false, false);

        List<ObligacionDelDia> delDia = armar(conPilar, sinPilar).dias().getFirst().obligaciones();

        assertThat(delDia).extracting(ObligacionDelDia::titulo, ObligacionDelDia::pilar)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Agua", "CUERPO"),
                        org.assertj.core.groups.Tuple.tuple("Otro", null));
    }

    @Test
    @DisplayName("spec 055 FR-013: los constructores anteriores de ObligacionDelDia siguen sirviendo, sin pilar")
    void losConstructoresAnterioresConservanSuForma() {
        ObligacionDelDia vieja = new ObligacionDelDia(UUID.randomUUID(), "Agua", "COMPLETADO", false, "NO_REQUERIDA",
                null, null, null, null, null, List.of());

        assertThat(vieja.pilar()).isNull();
    }

    private SemanaDelAlumno armar(ObligacionHabito... obligaciones) {
        ObligacionesHistoricasFinder historicas = (participantes, desde, hasta) -> List.of(obligaciones);
        EntregasPorRegistroFinder entregas = ids -> Map.of();
        ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
        when(participaciones.deParticipante(any())).thenReturn(Optional.of(new ParticipacionPrograma(ANA, true, 24,
                LocalDate.of(2026, 10, 17), LIMA, FasePrograma.PHASE_1_REBIRTH, UUID.randomUUID(), null,
                UserRole.TRAINEE, false, true)));
        UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
        PublicacionMuroFinder muro = mock(PublicacionMuroFinder.class);
        ArmadoDeLaSemana armado = new ArmadoDeLaSemana(historicas, entregas, participaciones, usuarios, muro,
                FixedClock.at(AHORA));
        return armado.semanaDe(ANA, UUID.randomUUID(), DIA, DIA);
    }
}
