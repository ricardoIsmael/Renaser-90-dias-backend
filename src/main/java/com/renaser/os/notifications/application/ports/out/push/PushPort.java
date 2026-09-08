package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

import java.util.List;

/**
 * Puerto hacia los proveedores de push. El adaptador web entrega Web Push con VAPID; los canales
 * nativos conservan su registro para que el adaptador móvil pueda incorporarse sin cambiar este
 * caso de uso.
 */
public interface PushPort {

    /** Best-effort: quien llame no debe asumir que esto garantiza entrega a un proveedor externo. */
    void enviar(List<TokenPush> tokens, String titulo, String cuerpo);
}
