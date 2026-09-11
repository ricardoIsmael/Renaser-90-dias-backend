package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

import java.util.List;

/**
 * Puerto hacia los proveedores de push. Un solo despachador reparte cada token al
 * {@link TransportePush} de su plataforma.
 *
 * <p>Sigue siendo best-effort: nadie debe asumir que esto garantiza que el mensaje llegó al
 * teléfono. Lo que ya no es aceptable es no enterarse de nada — de ahí que devuelva resultados.
 */
public interface PushPort {

    /**
     * @param rutaApp destino dentro de la app al tocar la notificación. Puede ser {@code null}.
     * @return un resultado por token. Nunca lanza: un proveedor caído no puede tumbar la emisión,
     *         que ya guardó la notificación en la bandeja.
     */
    List<ResultadoEnvioPush> enviar(List<TokenPush> tokens, String titulo, String cuerpo, String rutaApp);
}
