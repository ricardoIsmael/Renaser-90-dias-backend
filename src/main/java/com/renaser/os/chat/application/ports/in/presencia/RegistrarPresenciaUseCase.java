package com.renaser.os.chat.application.ports.in.presencia;

import com.renaser.os.shared.domain.UserId;

/**
 * Lo que el adaptador de WebSocket le cuenta a la aplicacion: este usuario abrio su primer
 * socket, o cerro el ultimo que le quedaba.
 *
 * <p>Primero/ultimo y no "un socket": una misma persona puede tener el telefono y la web
 * abiertos a la vez, y cerrar uno de los dos no la deja fuera de linea. Quien lleva esa
 * cuenta es el adaptador, que es el unico que ve las sesiones.
 */
public interface RegistrarPresenciaUseCase {

    void seConecto(UserId usuarioId);

    void seDesconecto(UserId usuarioId);

    /**
     * Renueva el vencimiento de quien sigue conectado. Lo llama el adaptador cada tanto.
     *
     * <p>Sin esto, las llaves de Redis venceria y alguien conectado y callado pasaria a
     * figurar como ausente. Con esto, ademas, una instancia que muere deja de refrescar y sus
     * usuarios se apagan solos en cuanto vence la llave — que es justo lo que se quiere.
     */
    void sigueConectado(UserId usuarioId);
}
