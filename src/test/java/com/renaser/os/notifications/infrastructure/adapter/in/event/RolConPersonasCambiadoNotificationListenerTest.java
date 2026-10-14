package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.plataforma.api.RolConPersonasCambiadoEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El push a Administración por un rol con personas que cambió (D-269). Sin Spring. */
class RolConPersonasCambiadoNotificationListenerTest {

    private static final Instant CUANDO = Instant.parse("2026-10-14T22:00:00Z");

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);

    private RolConPersonasCambiadoEvent evento(long personas, String motivo) {
        return new RolConPersonasCambiadoEvent(UUID.fromString("0f8d5c56-3f5b-4bb8-9f7a-2f1d1c1b0a01"),
                "Mentor junior", personas, "lo desactivó: quedan solo con su rol base (Mentor)",
                UserId.of(UUID.randomUUID()), motivo, CUANDO);
    }

    @Test
    @DisplayName("a todos los ADMIN y ALQUIMISTA activos, OPERACION_PLATAFORMA, sin ruta, con el mismo origen")
    void aAdministracion() {
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(admin, alquimista));

        new RolConPersonasCambiadoNotificationListener(emitir, participaciones).on(evento(3, "Terminó el piloto"));

        ArgumentCaptor<EmitirNotificacionCommand> avisos = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(avisos.capture());
        assertThat(avisos.getAllValues()).extracting(EmitirNotificacionCommand::usuarioId)
                .containsExactly(admin, alquimista);
        EmitirNotificacionCommand primero = avisos.getAllValues().getFirst();
        assertThat(primero.tipo()).isEqualTo(TipoNotificacion.OPERACION_PLATAFORMA);
        assertThat(primero.titulo()).isEqualTo("Soporte TI cambió un rol");
        assertThat(primero.cuerpo()).isEqualTo("Soporte TI cambió el rol «Mentor junior» (3 personas): lo desactivó: "
                + "quedan solo con su rol base (Mentor). Motivo: Terminó el piloto");
        assertThat(primero.rutaApp()).isNull();
        assertThat(avisos.getAllValues().get(1).origenEventoId()).isEqualTo(primero.origenEventoId());
    }

    @Test
    @DisplayName("una persona va en singular; sin motivo no dice «Motivo»")
    void singular() {
        assertThat(RolConPersonasCambiadoNotificationListener.cuerpo(evento(1, null)))
                .isEqualTo("Soporte TI cambió el rol «Mentor junior» (1 persona): lo desactivó: quedan solo con su rol "
                        + "base (Mentor).");
    }
}
