package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A quién avisa un cambio de interruptor (spec 002, FR-008; contrato §7). Sin Spring. */
class FuncionalidadCambiadaNotificationListenerTest {

    private static final Instant CUANDO = Instant.parse("2026-10-13T22:00:00Z");

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private FuncionalidadCambiadaNotificationListener oyente;

    @BeforeEach
    void armar() {
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.IT_SUPPORT))).thenReturn(List.of(soporte));
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(admin, alquimista));
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Ricardo", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        oyente = new FuncionalidadCambiadaNotificationListener(emitir, participaciones, usuarios);
    }

    @Test
    @DisplayName("un cambio que no es crítico ni apaga para todos: solo a Soporte TI, con la ruta de su consola")
    void soloASoporteTi() {
        oyente.on(evento("chat.audio", false, false));

        List<EmitirNotificacionCommand> avisos = avisos();
        assertThat(avisos).extracting(EmitirNotificacionCommand::usuarioId).containsExactly(soporte);
        assertThat(avisos.getFirst().tipo()).isEqualTo(TipoNotificacion.OPERACION_PLATAFORMA);
        assertThat(avisos.getFirst().rutaApp()).isEqualTo("/soporte-ti");
        assertThat(avisos.getFirst().cuerpo())
                .isEqualTo("Notas de voz: Apagada para Aprendiz. Lo hizo Ricardo. Motivo: Mantenimiento");
    }

    @Test
    @DisplayName("crítica o apagada para todos: también al Admin y al Alquimista, sin ruta, con el mismo origen")
    void criticaTambienAAdministracion() {
        oyente.on(evento("chat", true, false));
        oyente.on(evento("chat.audio", false, true));

        List<EmitirNotificacionCommand> avisos = avisos();
        assertThat(avisos).extracting(EmitirNotificacionCommand::usuarioId)
                .containsExactly(soporte, admin, alquimista, soporte, admin, alquimista);
        assertThat(avisos.get(1).rutaApp()).isNull();
        assertThat(avisos.get(1).origenEventoId()).isEqualTo(avisos.getFirst().origenEventoId());
    }

    @Test
    @DisplayName("idempotente: el mismo cambio entregado dos veces lleva el mismo origen (el índice único lo descarta)")
    void mismoOrigenEnUnaReentrega() {
        oyente.on(evento("chat.audio", false, false));
        oyente.on(evento("chat.audio", false, false));

        List<EmitirNotificacionCommand> avisos = avisos();
        assertThat(avisos.get(0).origenEventoId()).isEqualTo(avisos.get(1).origenEventoId());
    }

    private List<EmitirNotificacionCommand> avisos() {
        ArgumentCaptor<EmitirNotificacionCommand> capturados = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(capturados.capture());
        return capturados.getAllValues();
    }

    private FuncionalidadCambiadaEvent evento(String clave, boolean critica, boolean paraTodos) {
        return new FuncionalidadCambiadaEvent(clave, critica, paraTodos, soporte, "Mantenimiento", CUANDO,
                "Notas de voz", "Apagada para Aprendiz");
    }
}
