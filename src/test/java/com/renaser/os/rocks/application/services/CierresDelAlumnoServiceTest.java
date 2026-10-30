package com.renaser.os.rocks.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.api.CierresDelDiaFinder.ResumenDeCierres;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierresDelAlumnoUseCase.ConsultaCierres;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El guardián de la ruta del mentor (spec 017): acompañante vigente del grupo, alumno de ese grupo, cuenta activa. */
class CierresDelAlumnoServiceTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final CierresDelDiaFinder cierres = mock(CierresDelDiaFinder.class);
    private final CierresDeLaSemana respuesta = new CierresDeLaSemana(LUNES, LUNES, List.of(), new ResumenDeCierres(0, 0, List.of()));
    private final CierresDelAlumnoService servicio = new CierresDelAlumnoService(acompanamiento, usuarios, cierres,
            FixedClock.at(Instant.parse("2026-10-29T03:00:00Z")));

    @BeforeEach
    void preparar() {
        when(acompanamiento.acompanaVigente(eq(mentora), eq(fenix), any())).thenReturn(true);
        when(acompanamiento.aprendicesVigentes(eq(fenix), any())).thenReturn(List.of(ana));
        when(usuarios.findById(mentora)).thenReturn(Optional.of(new UserSummary(mentora, "Mentora", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(cierres.semanaDe(any(), any())).thenReturn(respuesta);
    }

    @Test
    @DisplayName("el mentor del grupo lee los cierres de su alumno de esa semana")
    void leeSuAlumno() {
        assertThat(servicio.semanaDe(new ConsultaCierres(mentora, fenix, ana, LUNES))).isSameAs(respuesta);
        verify(cierres).semanaDe(ana, LUNES);
    }

    @Test
    @DisplayName("quien no acompana ese grupo no lee nada, ni siquiera de su propio alumno")
    void otroGrupo() {
        UserId otro = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaCierres(otro, fenix, ana, LUNES))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(cierres);
    }

    @Test
    @DisplayName("un alumno de otro grupo, aunque el grupo sea el del mentor, es 403")
    void alumnoAjeno() {
        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaCierres(mentora, fenix, beto, LUNES))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(cierres);
    }

    @Test
    @DisplayName("el mentor con la cuenta suspendida no lee; si ni existe, 404")
    void cuentaDelMentor() {
        when(usuarios.findById(mentora)).thenReturn(Optional.of(new UserSummary(mentora, "Mentora", null, UserRole.MENTOR, UserStatus.SUSPENDED)));
        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaCierres(mentora, fenix, ana, LUNES))).isInstanceOf(NotAuthorizedException.class);

        when(usuarios.findById(mentora)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaCierres(mentora, fenix, ana, LUNES))).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(cierres);
    }
}
