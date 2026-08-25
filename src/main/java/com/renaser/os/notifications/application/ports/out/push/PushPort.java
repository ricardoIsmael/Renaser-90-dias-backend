package com.renaser.os.notifications.application.ports.out.push;

import java.util.List;

/**
 * Puerto hacia el proveedor de push (Expo, en el repo viejo: {@code chat/repository.ts:sendExpoPushNotifications}).
 * Hoy solo tiene el adaptador placeholder {@code NoOpPushAdapter} (sin credenciales Expo reales
 * todavia) — mismo patron que {@code shared/infrastructure/storage/NoOpAlmacenamientoAdapter}
 * para S3 (D-34): el puerto ya queda listo para el dia que exista el adaptador real.
 */
public interface PushPort {

    /** Best-effort: quien llame no debe asumir que esto garantiza entrega (Expo es HTTP externo,
     * puede fallar parcial o totalmente). Sin tokens, no hace nada. */
    void enviar(List<String> tokens, String titulo, String cuerpo);
}
