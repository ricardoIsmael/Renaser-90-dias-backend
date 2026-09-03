package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * El unico punto de entrada para crear una notificacion — lo llaman los 4 listeners de
 * eventos de {@code in/event/} (§ docs/MODULO_NOTIFICATIONS.md) y, a futuro, cualquier otro
 * modulo que necesite avisar algo por la bandeja.
 *
 * <p>Contrato heredado 1:1 de {@code notifications/service.ts:emit} del repo viejo: respeta
 * {@code PreferenciaNotificacion} — si el usuario apago ese tipo, NO se crea la fila. Sin fila
 * de preferencia, el default es HABILITADA (nunca se asume apagado por ausencia). A diferencia
 * del viejo `emit()`, esta implementacion NO traga excepciones: los listeners que la llaman
 * corren en su propia transaccion async post-commit (`@ApplicationModuleListener`), asi que un
 * fallo aca no puede tumbar la operacion que origino el evento — Spring Modulith ya la aisla.
 */
public interface EmitirNotificacionUseCase {

    /** Vacio cuando la preferencia del usuario esta explicitamente apagada para ese tipo, O
     * cuando el mismo {@code origenEventoId} ya genero una notificacion antes — C-7: el outbox
     * de Modulith es at-least-once, una redelivery del mismo evento no debe duplicar la fila
     * ni el push (ver {@code notificaciones_origen_evento_uk}, V16). */
    Optional<Notificacion> emitir(EmitirNotificacionCommand command);

    record EmitirNotificacionCommand(@NotNull UserId usuarioId, @NotNull TipoNotificacion tipo,
                                      @NotBlank String titulo, @NotBlank String cuerpo, String rutaApp,
                                      UUID origenEventoId) {

        public EmitirNotificacionCommand {
            SelfValidating.validateConstructorArgs(EmitirNotificacionCommand.class, usuarioId, tipo, titulo, cuerpo,
                    rutaApp, origenEventoId);
        }
    }
}
