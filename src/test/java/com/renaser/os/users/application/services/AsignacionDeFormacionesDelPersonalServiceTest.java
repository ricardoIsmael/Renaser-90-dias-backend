package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.GruposDelPersonalFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase.AsignarFormacionesCommand;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort.Pertenencia;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 022/025-6 A (E2E P3-L): al cambiar las formaciones del personal, su programa propio sin empezar las sigue. */
@ExtendWith(MockitoExtension.class)
class AsignacionDeFormacionesDelPersonalServiceTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-11-01T15:00:00Z"));

    @Mock
    private LoadUserPort usuarios;
    @Mock
    private CargarPertenenciaPort pertenencias;
    @Mock
    private FormacionesDelPersonalPort personal;
    @Mock
    private GruposDelPersonalFinder grupos;
    @Mock
    private CatalogoDeFormacionesPort catalogo;
    @Mock
    private LoadParticipacionProgramaPort participaciones;
    @Mock
    private SaveParticipacionProgramaPort guardar;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());

    private AsignacionDeFormacionesDelPersonalService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AsignacionDeFormacionesDelPersonalService(new RequireAdminGuard(usuarios), pertenencias, personal, grupos, catalogo,
                participaciones, guardar, RELOJ);
        when(usuarios.byId(admin)).thenReturn(Optional.of(User.rehydrate(admin, new Email(admin + "@renaser.com"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin", null, null, null, null)));
        when(pertenencias.de(mentor)).thenReturn(Optional.of(new Pertenencia(UserRole.MENTOR, Formacion.ESCUELA, 90, false,
                List.of(new Asignacion(Formacion.ESCUELA, true), new Asignacion(Formacion.DIPLOMADO, false)))));
    }

    private void programaSinEmpezarEn(Formacion formacion) {
        when(participaciones.byParticipanteId(mentor)).thenReturn(Optional.of(
                ParticipacionPrograma.inscribirPersonalSinActivar(mentor, RELOJ, formacion, 90)));
    }

    @Test
    @DisplayName("su programa sin empezar estaba en una formación que ya no tiene: pasa a la primera que sí tiene")
    void elProgramaSinEmpezarSigueSusFormaciones() {
        programaSinEmpezarEn(Formacion.ESCUELA);
        when(catalogo.de(Formacion.DIPLOMADO)).thenReturn(new FormacionConfigurada(Formacion.DIPLOMADO, "Diplomado Renaser", "", true, List.of(90, 365), 1));

        servicio.asignar(new AsignarFormacionesCommand(admin, mentor, Set.of(Formacion.DIPLOMADO), Formacion.DIPLOMADO));

        var guardada = org.mockito.ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(guardar).save(guardada.capture());
        assertThat(guardada.getValue().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(guardada.getValue().duracionDias()).isEqualTo(90);
    }

    @Test
    @DisplayName("si su programa sigue en una formación que conserva, no se toca")
    void siConservaLaFormacionDeSuProgramaNoSeToca() {
        programaSinEmpezarEn(Formacion.ESCUELA);

        servicio.asignar(new AsignarFormacionesCommand(admin, mentor, Set.of(Formacion.ESCUELA, Formacion.DIPLOMADO), Formacion.DIPLOMADO));

        verify(guardar, never()).save(any());
    }

    @Test
    @DisplayName("un programa que ya empezó no se reinicia")
    void unProgramaEnMarchaNoSeReinicia() {
        var enMarcha = ParticipacionPrograma.activarSeguimientoPersonal(mentor, RELOJ, Formacion.ESCUELA, 90);
        when(participaciones.byParticipanteId(mentor)).thenReturn(Optional.of(enMarcha));

        servicio.asignar(new AsignarFormacionesCommand(admin, mentor, Set.of(Formacion.DIPLOMADO), Formacion.DIPLOMADO));

        verify(guardar, never()).save(any());
    }
}
