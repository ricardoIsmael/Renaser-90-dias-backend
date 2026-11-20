package com.renaser.os.rocks.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.FaseDelPrograma;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.rocks.application.ports.in.objetivos.ConsultarObjetivosDelAlumnoUseCase.ConsultaObjetivos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los objetivos de un alumno para su mentor (spec 057, FR-001 a FR-003). */
class ObjetivosDelAlumnoServiceTest {

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ObjetivosDelAprendizFinder objetivos = mock(ObjetivosDelAprendizFinder.class);
    private final ObjetivosDelAprendiz respuesta = new ObjetivosDelAprendiz(List.of(), null, null, 365, new FaseDelPrograma(2, 29, 137));
    private final ObjetivosDelAlumnoService servicio = new ObjetivosDelAlumnoService(acompanamiento, usuarios, objetivos,
            FixedClock.at(Instant.parse("2026-10-29T03:00:00Z")));

    @BeforeEach
    void preparar() {
        when(acompanamiento.acompanaVigente(eq(mentora), eq(fenix), any())).thenReturn(true);
        when(acompanamiento.aprendicesVigentes(eq(fenix), any())).thenReturn(List.of(ana));
        when(usuarios.findById(mentora)).thenReturn(Optional.of(new UserSummary(mentora, "Mentora", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(objetivos.objetivosDe(ana)).thenReturn(respuesta);
    }

    @Test
    @DisplayName("spec 057 FR-001: el mentor del grupo lee los objetivos de su alumno, con el plazo de SU formación y su fase")
    void leeSuAlumno() { // spec 057 FR-001
        ObjetivosDelAprendiz leidos = servicio.objetivosDe(new ConsultaObjetivos(mentora, fenix, ana));

        assertThat(leidos).isSameAs(respuesta);
        assertThat(leidos.duracionDias()).isEqualTo(365);
        assertThat(leidos.fase().numero()).isEqualTo(2);
    }

    @Test
    @DisplayName("spec 057 FR-002: quien ya no acompaña el grupo, un alumno de otro grupo o una cuenta suspendida reciben 403 y nada se lee")
    void guardianDeAcompanamiento() { // spec 057 FR-002
        UserId exMentor = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> servicio.objetivosDe(new ConsultaObjetivos(exMentor, fenix, ana))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.objetivosDe(new ConsultaObjetivos(mentora, fenix, beto))).isInstanceOf(NotAuthorizedException.class);
        when(usuarios.findById(mentora)).thenReturn(Optional.of(new UserSummary(mentora, "Mentora", null, UserRole.MENTOR, UserStatus.SUSPENDED)));
        assertThatThrownBy(() -> servicio.objetivosDe(new ConsultaObjetivos(mentora, fenix, ana))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(objetivos);
    }

    @Test
    @DisplayName("spec 057 FR-003: un alumno suspendido o sin programa andando es 404 con codigo SIN_PROGRAMA_ACTIVO, no un 403 ni un 500")
    void sinProgramaActivo() { // spec 057 FR-003
        when(objetivos.objetivosDe(ana)).thenThrow(new NotAuthorizedException("suspendido"));

        assertThatThrownBy(() -> servicio.objetivosDe(new ConsultaObjetivos(mentora, fenix, ana)))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
                    assertThat(e.estado()).isEqualTo(404);
                    assertThat(e.codigo()).isEqualTo("SIN_PROGRAMA_ACTIVO");
                });
    }

    @Test
    @DisplayName("spec 057 FR-001: la fase se estira al plazo de la persona: 90 días da 8 a 34; 365 días, 29 a 137")
    void faseSegunElPlazo() { // spec 057 FR-001
        assertThat(ObjetivosDelAprendizService.faseDe(10, 90)).isEqualTo(new FaseDelPrograma(2, 8, 34));
        assertThat(ObjetivosDelAprendizService.faseDe(0, 90)).as("el día 0 es la fase 1").isEqualTo(new FaseDelPrograma(1, 1, 7));
        assertThat(ObjetivosDelAprendizService.faseDe(40, 365)).isEqualTo(new FaseDelPrograma(2, 29, 137));
    }
}
