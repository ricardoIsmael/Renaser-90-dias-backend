package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.CuentaDeGoogleRevocadaEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** El aviso de que la cuenta de Google de Renaser hay que volver a conectarla (spec 040, FR-009). */
@ExtendWith(MockitoExtension.class)
class CuentaDeGoogleRevocadaNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitir;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());

    // spec 040 FR-009, SC-005
    @Test
    @DisplayName("FR-009: avisa a ADMIN y ALCHEMIST activos con un aviso de sistema, sin ruta, y con el id del día como origen (un solo aviso por día)")
    void avisaAQuienesPuedenConectarla() {
        when(participacionFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST))).thenReturn(List.of(admin, alquimista));
        UUID avisoDelDia = UUID.randomUUID();

        new CuentaDeGoogleRevocadaNotificationListener(emitir, participacionFinder)
                .on(new CuentaDeGoogleRevocadaEvent(avisoDelDia, Instant.parse("2026-11-05T15:00:00Z")));

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, times(2)).emitir(captor.capture());
        assertThat(captor.getAllValues()).extracting(EmitirNotificacionCommand::usuarioId).containsExactly(admin, alquimista);
        assertThat(captor.getAllValues()).allSatisfy(c -> {
            assertThat(c.tipo()).isEqualTo(TipoNotificacion.ANUNCIO_SISTEMA);
            assertThat(c.titulo()).isEqualTo("Hay que volver a conectar Google");
            assertThat(c.cuerpo()).doesNotContain("token", "@gmail.com");
            assertThat(c.origenEventoId()).isEqualTo(avisoDelDia);
        });
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: si no hay ADMIN ni ALCHEMIST activos, no se emite nada y no falla")
    void sinNadieAQuienAvisar() {
        when(participacionFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST))).thenReturn(List.of());

        new CuentaDeGoogleRevocadaNotificationListener(emitir, participacionFinder)
                .on(new CuentaDeGoogleRevocadaEvent(UUID.randomUUID(), Instant.parse("2026-11-05T15:00:00Z")));

        verify(emitir, never()).emitir(any());
    }
}
