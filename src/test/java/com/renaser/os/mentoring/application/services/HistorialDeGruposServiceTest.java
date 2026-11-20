package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAprendiz;
import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase.GrupoAnterior;
import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase.PersonaAnterior;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.FilaDeGrupo;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.RankingDeGrupos;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** «Mis grupos anteriores» del mentor (spec 057, FR-020 y FR-021). */
class HistorialDeGruposServiceTest {

    /** Viernes 20 de noviembre de 2026, 10:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID aurora = UUID.randomUUID();
    private final UUID cohorte = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final SemaforoFinder semaforo = mock(SemaforoFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ConsultarRankingDeGruposUseCase ranking = mock(ConsultarRankingDeGruposUseCase.class);
    private final HistorialDeGruposService servicio = new HistorialDeGruposService(acompanamiento, semaforo, usuarios, ranking,
            FixedClock.at(AHORA));

    @BeforeEach
    void preparar() {
        when(acompanamiento.grupo(fenix)).thenReturn(Optional.of(new GrupoBasico(fenix, "Fénix", cohorte, "America/Lima")));
        when(acompanamiento.grupo(aurora)).thenReturn(Optional.of(new GrupoBasico(aurora, "Aurora", cohorte, "America/Lima")));
        when(usuarios.findByIds(any())).thenReturn(Map.of(
                ana, new UserSummary(ana, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                beto, new UserSummary(beto, "Beto Ríos", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(ranking.ranking(any(), any(), any())).thenReturn(new RankingDeGrupos(cohorte, "2026-10", "America/Lima", "v1", List.of()));
    }

    private static VentanaDelSemaforo semana(LocalDate viernes, String porcentaje, ColorSemaforo color) {
        return new VentanaDelSemaforo(viernes.minusDays(6), viernes, new BigDecimal(porcentaje), color, 7, true, List.of());
    }

    private void tramoCerradoDeFenix() {
        // Lima: del 1 de setiembre al 15 de octubre (el último día que lo acompañó es el 14, un miércoles).
        when(acompanamiento.tramosDeAcompanante(eq(mentora), any(), any())).thenReturn(List.of(new TramoDeAcompanamiento(fenix, "Fénix",
                Instant.parse("2026-09-01T05:00:00Z"), Instant.parse("2026-10-15T05:00:00Z"))));
        when(acompanamiento.tramosDeAprendices(eq(fenix), any(), any())).thenReturn(List.of(
                new TramoDeAprendiz(ana, Instant.parse("2026-09-01T05:00:00Z"), null),
                new TramoDeAprendiz(beto, Instant.parse("2026-09-01T05:00:00Z"), null)));
    }

    @Test
    @DisplayName("spec 057 FR-021: el color y el porcentaje son los de la última semana cerrada DENTRO del tramo (viernes 9 de octubre), no los de hoy")
    void elCierreEsLaUltimaSemanaDelTramo() { // spec 057 FR-020, FR-021
        tramoCerradoDeFenix();
        LocalDate viernes = LocalDate.of(2026, 10, 9);
        when(semaforo.semanaDe(any(), eq(viernes))).thenReturn(Map.of(
                ana, semana(viernes, "90", ColorSemaforo.VERDE), beto, semana(viernes, "40", ColorSemaforo.ROJO)));

        GrupoAnterior grupo = servicio.historialDe(mentora).grupos().getFirst();

        verify(semaforo).semanaDe(any(), eq(viernes));
        assertThat(grupo.nombre()).isEqualTo("Fénix");
        assertThat(grupo.desde()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(grupo.hasta()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(grupo.aprendices()).isEqualTo(2);
        assertThat(grupo.porcentajeFinal()).isEqualByComparingTo("65");
        assertThat(grupo.colorFinal()).isEqualTo("AMARILLO");
        assertThat(grupo.personas()).extracting(PersonaAnterior::nombre, PersonaAnterior::colorFinal)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Ana Pérez", "VERDE"), org.assertj.core.api.Assertions.tuple("Beto Ríos", "ROJO"));
    }

    @Test
    @DisplayName("spec 057 FR-021: las personas del grupo anterior no llevan ids ni datos de hoy: solo nombre y color")
    void sinIdsDePersonas() { // spec 057 FR-021
        assertThat(Arrays.stream(PersonaAnterior.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("nombre", "colorFinal");
    }

    @Test
    @DisplayName("spec 057 FR-020: el puesto final es el del ranking del último mes del tramo (octubre)")
    void puestoFinal() { // spec 057 FR-020
        tramoCerradoDeFenix();
        when(ranking.ranking(mentora, cohorte, YearMonth.of(2026, 10))).thenReturn(new RankingDeGrupos(cohorte, "2026-10", "America/Lima", "v1",
                List.of(new FilaDeGrupo(1, aurora, "Aurora", new BigDecimal("88"), 5, 8, 9, "CALCULADA"),
                        new FilaDeGrupo(2, fenix, "Fénix", new BigDecimal("70"), 5, 7, 10, "CALCULADA"),
                        new FilaDeGrupo(3, UUID.randomUUID(), "Sin muestra", null, 0, 0, 0, "SIN_MUESTRA"))));

        GrupoAnterior grupo = servicio.historialDe(mentora).grupos().getFirst();

        assertThat(grupo.puestoFinal().posicion()).isEqualTo(2);
        assertThat(grupo.puestoFinal().total()).as("el que no tiene muestra no cuenta").isEqualTo(2);
    }

    @Test
    @DisplayName("spec 057 FR-020: un tramo que no alcanzó a cerrar ninguna semana queda con color SIN_DATOS y sin porcentaje, no con el de hoy")
    void tramoSinSemanaCerrada() { // spec 057 FR-021
        // Del lunes 5 al miércoles 7 de octubre: ningún viernes dentro del tramo.
        when(acompanamiento.tramosDeAcompanante(eq(mentora), any(), any())).thenReturn(List.of(new TramoDeAcompanamiento(fenix, "Fénix",
                Instant.parse("2026-10-05T05:00:00Z"), Instant.parse("2026-10-08T05:00:00Z"))));
        when(acompanamiento.tramosDeAprendices(eq(fenix), any(), any())).thenReturn(List.of(new TramoDeAprendiz(ana, Instant.parse("2026-09-01T05:00:00Z"), null)));

        GrupoAnterior grupo = servicio.historialDe(mentora).grupos().getFirst();

        assertThat(grupo.colorFinal()).isEqualTo("SIN_DATOS");
        assertThat(grupo.porcentajeFinal()).isNull();
        org.mockito.Mockito.verifyNoInteractions(semaforo);
    }

    @Test
    @DisplayName("spec 057 FR-020: el grupo que sigue en curso no es anterior; el abierto de un grupo que ya terminó sí, hasta su último día; del más reciente al más viejo")
    void soloLosAnteriores() { // spec 057 FR-020
        when(acompanamiento.tramosDeAcompanante(eq(mentora), any(), any())).thenReturn(List.of(
                new TramoDeAcompanamiento(fenix, "Fénix", Instant.parse("2026-09-01T05:00:00Z"), Instant.parse("2026-10-15T05:00:00Z")),
                new TramoDeAcompanamiento(aurora, "Aurora", Instant.parse("2026-10-20T05:00:00Z"), null)));
        when(acompanamiento.tramosDeAprendices(any(), any(), any())).thenReturn(List.of());
        when(acompanamiento.finDelGrupoYaTerminado(eq(aurora), any())).thenReturn(Optional.of(LocalDate.of(2026, 11, 12)));

        List<GrupoAnterior> grupos = servicio.historialDe(mentora).grupos();

        assertThat(grupos).extracting(GrupoAnterior::nombre).containsExactly("Aurora", "Fénix");
        assertThat(grupos.getFirst().hasta()).isEqualTo(LocalDate.of(2026, 11, 12));

        when(acompanamiento.finDelGrupoYaTerminado(eq(aurora), any())).thenReturn(Optional.empty());
        assertThat(servicio.historialDe(mentora).grupos()).extracting(GrupoAnterior::nombre).containsExactly("Fénix");
    }

    @Test
    @DisplayName("spec 057 FR-020: el historial pide los tramos de mentor Y de guía (tramosDeAcompanante), no solo los de mentor")
    void incluyeLosTramosDeGuia() { // spec 057 FR-020
        tramoCerradoDeFenix();

        servicio.historialDe(mentora);

        verify(acompanamiento).tramosDeAcompanante(eq(mentora), any(), any());
        verify(acompanamiento, org.mockito.Mockito.never()).tramosDeMentor(any(), any(), any());
    }

    @Test
    @DisplayName("spec 057 FR-020: sin grupos anteriores la lista va vacía")
    void sinAnteriores() { // spec 057 FR-020
        when(acompanamiento.tramosDeAcompanante(eq(mentora), any(), any())).thenReturn(List.of());

        assertThat(servicio.historialDe(mentora).grupos()).isEmpty();
    }
}
