package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase.ActualizarDuracionAudioterapiaCommand;
import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase.AudioterapiaActualizada;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;
import com.renaser.os.habits.application.ports.out.audioterapia.SaveAudioterapiaPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioterapiaAdminServiceTest {

    @Mock
    private SaveAudioterapiaPort savePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private AudioterapiaAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId trainee = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new AudioterapiaAdminService(savePort, new HabitoAdminGuard(userSummaryFinder));
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(trainee)).thenReturn(
                Optional.of(new UserSummary(trainee, "Trainee", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @Test
    void actualizarComoAdminFunciona() {
        when(savePort.actualizarDuracion(1, 10)).thenReturn(
                new Audioterapia(1, "Semana 1", "ruta/1.mp3", "audio/mpeg", 1000, 10));

        AudioterapiaActualizada resultado = service.actualizar(new ActualizarDuracionAudioterapiaCommand(admin, 1, 10));

        assertThat(resultado.duracionDias()).isEqualTo(10);
    }

    @Test
    void actualizarComoTraineeEsRechazado() {
        var command = new ActualizarDuracionAudioterapiaCommand(trainee, 1, 10);

        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void actualizarSemanaInexistenteFalla() {
        when(savePort.actualizarDuracion(99, 10))
                .thenThrow(new java.util.NoSuchElementException("No existe audioterapia para la semana 99"));
        var command = new ActualizarDuracionAudioterapiaCommand(admin, 99, 10);

        assertThatThrownBy(() -> service.actualizar(command))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
