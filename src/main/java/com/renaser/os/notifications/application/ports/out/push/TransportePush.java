package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

/**
 * Un canal de entrega concreto: Web Push con VAPID, Expo para los nativos, el que venga después.
 *
 * <p>Se separa de {@link PushPort} para que agregar un proveedor no obligue a tocar el despacho
 * ni el caso de uso. Cada transporte declara qué plataforma atiende y responde qué pasó; decidir
 * a cuál mandarle cada token es trabajo del despachador.
 *
 * @param rutaApp destino lógico dentro de la app. Viaja como dato del push para que al tocarlo se
 *                abra donde corresponde — el contenido sigue siendo discreto y el detalle se carga
 *                adentro, ya autorizado (plan.md §9).
 */
public interface TransportePush {

    /**
     * Si este canal atiende esa plataforma. Es una pregunta y no una propiedad porque un mismo
     * proveedor puede cubrir varias: Expo entrega a iOS y a Android con la misma credencial y el
     * mismo formato, así que obligarlo a declarar una sola forzaría dos beans casi idénticos.
     */
    boolean atiende(PlataformaPush plataforma);

    /** Nombre para los logs y el diagnóstico. */
    String nombre();

    /** No lanza: los fallos se devuelven como estado. Un canal caído no puede tumbar a los otros. */
    ResultadoEnvioPush entregar(TokenPush token, String titulo, String cuerpo, String rutaApp);
}
