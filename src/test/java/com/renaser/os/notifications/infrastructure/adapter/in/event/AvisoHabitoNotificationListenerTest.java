package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * E-302 / D-184: el aviso de habito respeta el recordatorio que el aprendiz configuro. Con el
 * codigo anterior el listener llamaba a {@code emitir(command)} sin entrega (push a todos los
 * dispositivos) sin importar la preferencia, y estas verificaciones fallaban.
 */
@ExtendWith(MockitoExtension.class)
class AvisoHabitoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    private static AvisoHabitoDebidoEvent aviso(String tipo, Boolean activo, Integer minutos) {
        return new AvisoHabitoDebidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), "Meditar", tipo, 10, 10,
                UUID.randomUUID(), activo, minutos, Instant.parse("2026-09-06T01:50:00Z"));
    }

    @Test
    @DisplayName("recordatorio apagado -> fila en la bandeja, sin push")
    void apagadoSoloBandeja() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase).on(aviso("INICIO", false, null));

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture(), eq(EntregaPush.NINGUNO));
        assertThat(captor.getValue().tipo()).isEqualTo(TipoNotificacion.RECORDATORIO_HABITO);
    }

    @Test
    @DisplayName("inicio con alarma local en el telefono -> push solo al navegador")
    void inicioConAlarmaLocal() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase).on(aviso("INICIO", true, 30));

        verify(emitirNotificacionUseCase).emitir(org.mockito.ArgumentMatchers.any(), eq(EntregaPush.SOLO_NAVEGADOR));
    }

    @Test
    @DisplayName("vencimiento con recordatorio encendido -> push a todos (el telefono no tiene alarma para eso)")
    void vencimientoATodos() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase).on(aviso("POR_VENCER", true, 30));

        verify(emitirNotificacionUseCase).emitir(org.mockito.ArgumentMatchers.any(), eq(EntregaPush.TODOS));
    }
}
