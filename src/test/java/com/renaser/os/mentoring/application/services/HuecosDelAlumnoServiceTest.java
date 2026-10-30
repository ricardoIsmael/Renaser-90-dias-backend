package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.ports.in.ConsultarHuecosDelAlumnoUseCase.ConsultaHuecos;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El guardián de la ruta del mentor (spec 015): el mismo del Código Renaser del alumno. Solo el acompañante VIGENTE del
 * grupo, con la cuenta activa, y un alumno que hoy es de ESE grupo.
 */
class HuecosDelAlumnoServiceTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-10-28T03:00:00Z"));

    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId alumno = UserId.of(UUID.randomUUID());
    private final UUID grupo = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final HuecosDeHorarioFinder huecos = mock(HuecosDeHorarioFinder.class);
    private final HuecosDeLaSemana respuesta = ArmadoDeLosHuecos.armar(
            SemanasDeHuecosDePrueba.delContrato(LocalDate.of(2026, 10, 26)),
            new ArmadoDeLosHuecos.Lectura(null, false, LocalDate.of(2026, 10, 29)));

    private HuecosDelAlumnoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new HuecosDelAlumnoService(acompanamiento, usuarios, huecos, RELOJ);
        when(acompanamiento.acompanaVigente(mentor, grupo, RELOJ.now())).thenReturn(true);
        when(acompanamiento.aprendicesVigentes(grupo, RELOJ.now())).thenReturn(List.of(alumno));
        when(usuarios.findById(mentor)).thenReturn(Optional.of(
                new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(huecos.semanaDe(any(), any())).thenReturn(respuesta);
    }

    @Test
    @DisplayName("el acompanante vigente ve los huecos de un alumno de su grupo, de la semana pedida")
    void acompananteVigente() {
        LocalDate semana = LocalDate.of(2026, 10, 19);

        assertThat(servicio.semanaDe(new ConsultaHuecos(mentor, grupo, alumno, semana))).isSameAs(respuesta);

        verify(huecos).semanaDe(alumno, semana);
    }

    @Test
    @DisplayName("autorizacion negativa: quien no acompana ese grupo recibe 403 y no se calcula nada")
    void noAcompanaElGrupo() {
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaHuecos(otro, grupo, alumno, null)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("No acompanas");
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: un alumno de otro grupo no se lee pasando el id del grupo propio")
    void alumnoDeOtroGrupo() {
        UserId deOtroGrupo = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaHuecos(mentor, grupo, deOtroGrupo, null)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("no pertenece");
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: un mentor con la cuenta suspendida recibe 403 aunque aun acompane el grupo (E-258)")
    void mentorSuspendido() {
        when(usuarios.findById(mentor)).thenReturn(Optional.of(
                new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaHuecos(mentor, grupo, alumno, null)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("suspendida");
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("un actor que no existe es 404")
    void actorInexistente() {
        when(usuarios.findById(mentor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaHuecos(mentor, grupo, alumno, null)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
