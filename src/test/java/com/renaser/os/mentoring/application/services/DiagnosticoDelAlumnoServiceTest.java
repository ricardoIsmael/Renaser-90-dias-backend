package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarDiagnosticoDelAlumnoUseCase.ConsultaDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LectorDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.PersonaLeida;
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
import java.util.List;
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

/** El diagnóstico de un alumno para su mentor o su guía (spec 026, 026-11): el mismo guardián que los huecos del alumno. */
class DiagnosticoDelAlumnoServiceTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"));

    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId alumno = UserId.of(UUID.randomUUID());
    private final UUID grupo = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final DiagnosticoDeLaPersonaFinder diagnostico = mock(DiagnosticoDeLaPersonaFinder.class);
    private final LecturaDelDiagnostico lectura = LecturaDelDiagnostico.sinDiagnostico(new PersonaLeida(UUID.randomUUID().toString(), "Ana"),
            "ESTANDAR", "NO_APLICA", null, null);

    private DiagnosticoDelAlumnoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new DiagnosticoDelAlumnoService(acompanamiento, usuarios, diagnostico, RELOJ);
        when(acompanamiento.acompanaVigente(mentor, grupo, RELOJ.now())).thenReturn(true);
        when(acompanamiento.aprendicesVigentes(grupo, RELOJ.now())).thenReturn(List.of(alumno));
        when(usuarios.findById(mentor)).thenReturn(Optional.of(new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(diagnostico.lecturaDe(eq(alumno), any())).thenReturn(lectura);
    }

    @Test
    @DisplayName("el acompañante vigente lee el diagnóstico de un alumno de su grupo, y se dice quién lo abre y desde qué grupo")
    void acompananteVigente() {
        assertThat(servicio.diagnosticoDe(new ConsultaDiagnostico(mentor, grupo, alumno))).isSameAs(lectura);
        verify(diagnostico).lecturaDe(alumno, new LectorDelDiagnostico(mentor, grupo, mentor));
    }

    @Test
    @DisplayName("autorización negativa: quien no acompaña ese grupo recibe 403 y no se lee nada")
    void noAcompanaElGrupo() {
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.diagnosticoDe(new ConsultaDiagnostico(otro, grupo, alumno)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("No acompanas");
        verifyNoInteractions(diagnostico);
    }

    @Test
    @DisplayName("autorización negativa: un alumno de otro grupo no se lee pasando el id del grupo propio")
    void alumnoDeOtroGrupo() {
        UserId deOtroGrupo = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.diagnosticoDe(new ConsultaDiagnostico(mentor, grupo, deOtroGrupo)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("no pertenece");
        verifyNoInteractions(diagnostico);
    }

    @Test
    @DisplayName("autorización negativa: el acompañante con la cuenta suspendida recibe 403 (el interceptor no mira a MENTOR)")
    void cuentaSuspendida() {
        when(usuarios.findById(mentor)).thenReturn(Optional.of(new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> servicio.diagnosticoDe(new ConsultaDiagnostico(mentor, grupo, alumno))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(diagnostico);
    }
}
