package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.chat.api.SoporteDelAprendizFinder;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-244: el aviso a quienes atienden soporte cuando un aprendiz pide ayuda por una emergencia. */
@ExtendWith(MockitoExtension.class)
class EmergenciaPedidaNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitir;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private SoporteDelAprendizFinder soporteFinder;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final EmergenciaPedidaEvent pedido =
            new EmergenciaPedidaEvent(UUID.randomUUID(), ana, "Me operaron de la rodilla", 12, 20);

    private EmergenciaPedidaNotificationListener oyente() {
        return new EmergenciaPedidaNotificationListener(emitir, participacionFinder, userSummaryFinder, soporteFinder);
    }

    @Test
    @DisplayName("a ADMIN y ALCHEMIST, con el pedido como origen y la ruta a su chat de soporte; sin lo que escribió")
    void avisaAQuienesAtiendenSoporte() {
        UUID soporte = UUID.randomUUID();
        when(participacionFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(admin, alquimista));
        when(userSummaryFinder.findById(ana)).thenReturn(Optional.of(
                new UserSummary(ana, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(soporteFinder.conversacionDeSoporteDe(ana)).thenReturn(Optional.of(soporte));

        oyente().on(pedido);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, times(2)).emitir(captor.capture());
        assertThat(captor.getAllValues()).extracting(EmitirNotificacionCommand::usuarioId).containsExactly(admin, alquimista);
        assertThat(captor.getAllValues()).allSatisfy(c -> {
            assertThat(c.tipo()).isEqualTo(TipoNotificacion.TICKET_ABIERTO);
            assertThat(c.titulo()).isEqualTo("Emergencia de un aprendiz");
            assertThat(c.cuerpo()).isEqualTo("Ana Pérez pide volver al día 12 (hoy está en el día 20). "
                    + "Te espera en su chat de soporte.");
            assertThat(c.cuerpo()).doesNotContain("rodilla");
            assertThat(c.rutaApp()).isEqualTo("/chat/" + soporte);
            assertThat(c.origenEventoId()).isEqualTo(pedido.solicitudId());
        });
    }

    @Test
    @DisplayName("sin chat de soporte el aviso sale igual, sin ruta")
    void sinSoporteSaleSinRuta() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of(admin));
        when(userSummaryFinder.findById(ana)).thenReturn(Optional.empty());
        when(soporteFinder.conversacionDeSoporteDe(ana)).thenReturn(Optional.empty());

        oyente().on(pedido);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir).emitir(captor.capture());
        assertThat(captor.getValue().rutaApp()).isNull();
        assertThat(captor.getValue().cuerpo()).startsWith("Un aprendiz pide volver al día 12");
    }

    @Test
    @DisplayName("sin nadie que atienda no emite nada")
    void sinAdministradores() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of());

        oyente().on(pedido);

        verify(emitir, never()).emitir(any());
    }
}
