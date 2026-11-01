package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase.CambiarDuracionCommand;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 025 (025-2 A): cambiar la duración de quien ya empezó; aquí, las respuestas de los casos que NO son un aprendiz inscrito. */
@ExtendWith(MockitoExtension.class)
class CambioDeDuracionDelProgramaServiceTest {

    @Mock
    private LoadUserPort usuarios;
    @Mock
    private LoadParticipacionProgramaPort participaciones;
    @Mock
    private SaveParticipacionProgramaPort guardarParticipacion;
    @Mock
    private SaveAjusteDiaProgramaPort bitacora;
    @Mock
    private CatalogoDeFormacionesPort catalogo;
    @Mock
    private IdGenerator ids;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId persona = UserId.of(UUID.randomUUID());

    private CambioDeDuracionDelProgramaService servicio() {
        return new CambioDeDuracionDelProgramaService(new RequireAdminGuard(usuarios), usuarios, participaciones, guardarParticipacion,
                bitacora, catalogo, ids, FixedClock.at(Instant.parse("2026-11-01T15:00:00Z")));
    }

    private void existe(UserId id, UserRole rol) {
        when(usuarios.byId(id)).thenReturn(Optional.of(User.rehydrate(id, new Email(id + "@renaser.com"), rol,
                UserStatus.ACTIVE, "Persona de prueba", null, null, null, null)));
    }

    @Test
    @DisplayName("E2E P4-I: el personal sin programa es un 409 (no es aprendiz), no el 404 de «no inscripto»")
    void elPersonalNoTieneDuracionPropia() {
        existe(admin, UserRole.ADMIN);
        existe(persona, UserRole.MENTOR);

        assertThatThrownBy(() -> servicio().cambiar(new CambiarDuracionCommand(admin, persona, 365, "Pidió más tiempo")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(participaciones, guardarParticipacion, bitacora);
    }

    @Test
    @DisplayName("una persona que no existe sigue siendo 404")
    void unaPersonaQueNoExisteEsNoEncontrada() {
        when(usuarios.byId(persona)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().cambiar(new CambiarDuracionCommand(admin, persona, 365, "Pidió más tiempo")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un aprendiz sin participación sigue siendo 404")
    void unAprendizSinProgramaEsNoEncontrado() {
        existe(admin, UserRole.ADMIN);
        existe(persona, UserRole.TRAINEE);
        when(participaciones.byParticipanteId(persona)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().cambiar(new CambiarDuracionCommand(admin, persona, 365, "Pidió más tiempo")))
                .isInstanceOf(NoSuchElementException.class);
    }
}
