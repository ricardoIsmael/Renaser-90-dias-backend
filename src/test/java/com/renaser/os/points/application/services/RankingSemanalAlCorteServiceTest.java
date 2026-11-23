package com.renaser.os.points.application.services;

import com.renaser.os.points.api.MedicionAcumuladaFinder;
import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.points.api.RankingSemanalFinder.PuestoDeLaSemana;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Coherencia, Kilómetros y Gimnasio de una semana (D-367): las mismas personas y reglas que las pestañas, y lo de la semana. */
class RankingSemanalAlCorteServiceTest {

    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 22);
    private static final LocalDate DOMINGO_ANTERIOR = LocalDate.of(2026, 11, 15);

    private final LoadRankingCandidatosPort candidatos = mock(LoadRankingCandidatosPort.class);
    private final PorcentajeRocasFinder rocas = mock(PorcentajeRocasFinder.class);
    private final MedicionAcumuladaFinder mediciones = mock(MedicionAcumuladaFinder.class);
    private final CuentasCerradasFinder cerradas = mock(CuentasCerradasFinder.class);
    private final RankingSemanalAlCorteService servicio = new RankingSemanalAlCorteService(candidatos, rocas, mediciones, cerradas);

    private final UserId ana = id(), beto = id(), carla = id(), cerrada = id();

    private void padron() {
        when(candidatos.aprendicesActivosConPuntaje()).thenReturn(List.of(candidato(ana, "Ana", Formacion.ESCUELA),
                candidato(beto, "Beto", Formacion.ESCUELA), candidato(carla, "Carla", Formacion.ESCUELA),
                candidato(cerrada, "Cerrada", Formacion.ESCUELA), candidato(id(), "Rosa", Formacion.DIPLOMADO)));
        when(cerradas.cerradasEntre(any())).thenReturn(Set.of(cerrada));
    }

    @Test
    @DisplayName("coherencia: el % de los 7 días hasta el domingo, de mayor a menor; cerradas y otras formaciones fuera; sin dato = 0")
    void coherencia() {
        padron();
        when(rocas.porcentajePorParticipante(any(), eq(DOMINGO))).thenReturn(Map.of(ana, new BigDecimal("80.0"),
                beto, new BigDecimal("95.5"), cerrada, new BigDecimal("100.0")));

        List<PuestoDeLaSemana> ranking = servicio.coherencia(DOMINGO, Formacion.ESCUELA);

        assertThat(ranking).extracting(PuestoDeLaSemana::nombreCompleto).containsExactly("Beto", "Ana", "Carla");
        assertThat(ranking).extracting(PuestoDeLaSemana::valor)
                .containsExactly(new BigDecimal("95.5"), new BigDecimal("80.0"), BigDecimal.ZERO);
    }

    @Test
    @DisplayName("kilómetros: lo de la semana es el acumulado al domingo menos el del domingo anterior, no el acumulado del programa")
    void kilometrosDeLaSemana() {
        padron();
        when(mediciones.kilometrosAcumulados(any(), eq(DOMINGO))).thenReturn(Map.of(ana, new BigDecimal("120.50"),
                beto, new BigDecimal("300.00"), carla, new BigDecimal("8.00"), cerrada, new BigDecimal("999")));
        when(mediciones.kilometrosAcumulados(any(), eq(DOMINGO_ANTERIOR))).thenReturn(Map.of(ana, new BigDecimal("100.00"),
                beto, new BigDecimal("290.25")));

        List<PuestoDeLaSemana> ranking = servicio.kilometros(DOMINGO, Formacion.ESCUELA);

        assertThat(ranking).extracting(PuestoDeLaSemana::nombreCompleto).containsExactly("Ana", "Beto", "Carla");
        assertThat(ranking).extracting(PuestoDeLaSemana::valor)
                .as("Ana: 120,50 - 100,00; Beto: 300,00 - 290,25 (el acumulado más alto no gana); Carla empezó esta semana")
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("20.50"), new BigDecimal("9.75"), new BigDecimal("8.00"));
    }

    @Test
    @DisplayName("gimnasio: los días de la semana (acumulado menos acumulado anterior); quien no fue ninguno entra con 0 y nunca sale negativo")
    void gimnasioDeLaSemana() {
        padron();
        when(mediciones.diasDeGimnasio(any(), eq(DOMINGO))).thenReturn(Map.of(ana, 15, beto, 7, carla, 2));
        when(mediciones.diasDeGimnasio(any(), eq(DOMINGO_ANTERIOR))).thenReturn(Map.of(ana, 11, beto, 7, carla, 3));

        List<PuestoDeLaSemana> ranking = servicio.gimnasio(DOMINGO, Formacion.ESCUELA);

        assertThat(ranking).extracting(PuestoDeLaSemana::nombreCompleto).startsWith("Ana").containsExactlyInAnyOrder("Ana", "Beto", "Carla");
        assertThat(ranking).extracting(p -> p.valor().intValue()).containsExactly(4, 0, 0);
    }

    @Test
    @DisplayName("cada formación compara solo a los suyos y sin candidatos no consulta nada")
    void porFormacion() {
        padron();
        when(mediciones.diasDeGimnasio(any(), any())).thenReturn(Map.of());

        assertThat(servicio.gimnasio(DOMINGO, Formacion.DIPLOMADO)).extracting(PuestoDeLaSemana::nombreCompleto).containsExactly("Rosa");
        assertThat(servicio.gimnasio(DOMINGO, Formacion.CERTIFICACION)).isEmpty();
        when(candidatos.aprendicesActivosConPuntaje()).thenReturn(List.of());
        assertThat(servicio.coherencia(DOMINGO, Formacion.ESCUELA)).isEmpty();
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static CandidatoRanking candidato(UserId id, String nombre, Formacion formacion) {
        return new CandidatoRanking(id, nombre, 0, BigDecimal.ZERO, formacion);
    }
}
