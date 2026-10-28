package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
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
    default Optional<Notificacion> emitir(EmitirNotificacionCommand command) {
        return emitir(command, EntregaPush.TODOS);
    }

    /**
     * Igual que {@link #emitir(EmitirNotificacionCommand)}, eligiendo a que dispositivos se empuja
     * ademas de la bandeja (D-184). La bandeja no cambia: preferencia, deduplicacion y fila son las
     * mismas; {@code entregaPush} solo decide el empujon.
     *
     * <p>Va como argumento aparte y no como campo del comando porque {@code SelfValidating} valida
     * contra el UNICO constructor del record: un segundo constructor "de compatibilidad" para los
     * veintitantos llamadores de siempre lo habria vuelto ambiguo.
     */
    Optional<Notificacion> emitir(EmitirNotificacionCommand command, EntregaPush entregaPush);

    /**
     * Igual que {@link #emitir(EmitirNotificacionCommand, EntregaPush)}, ademas con la etiqueta del push
     * (spec 007, FR-014): el aviso con la misma etiqueta REEMPLAZA al que ya esta en la bandeja del telefono
     * en vez de apilarse (Android {@code tag}, iOS {@code threadId}, navegador {@code tag}). La usa el aviso
     * de un habito, para que el del servidor y la alarma local del telefono sean uno solo.
     *
     * <p>Va como argumento y no como campo del comando por lo mismo que {@code entregaPush}. La etiqueta
     * solo afecta al push: bandeja, preferencia y deduplicacion no cambian. {@code null} = la de siempre
     * (la de chat se deriva de la ruta, D-221).
     */
    Optional<Notificacion> emitir(EmitirNotificacionCommand command, EntregaPush entregaPush, String etiquetaDelPush);

    /**
     * Igual que {@link #emitir(EmitirNotificacionCommand, EntregaPush, String)}, ademas con el instante en que
     * el aviso deja de tener sentido (E-645, issue #22): el push lleva esa caducidad para que el proveedor
     * (FCM guarda un mensaje sin caducidad hasta 4 semanas) NO lo entregue tarde cuando el telefono recupere la
     * red o salga del reposo. «Te toca a las 07:00» llegaba pasadas las 8.
     *
     * <p>La usan los avisos que son «a una hora»: un habito (su hora, o su plazo si es de vencimiento) y un
     * recordatorio de evento (su inicio). Va como argumento y no como campo del comando por lo mismo que la
     * etiqueta. Solo afecta al push: bandeja, preferencia y deduplicacion no cambian. {@code null} = no caduca
     * (chat, logros y avisos manuales, como siempre).
     */
    Optional<Notificacion> emitir(EmitirNotificacionCommand command, EntregaPush entregaPush, String etiquetaDelPush,
                                  Instant venceEn);

    record EmitirNotificacionCommand(@NotNull UserId usuarioId, @NotNull TipoNotificacion tipo,
                                      @NotBlank String titulo, @NotBlank String cuerpo, String rutaApp,
                                      UUID origenEventoId) {

        public EmitirNotificacionCommand {
            SelfValidating.validateConstructorArgs(EmitirNotificacionCommand.class, usuarioId, tipo, titulo, cuerpo,
                    rutaApp, origenEventoId);
        }
    }
}
