package com.renaser.os.points.application.ports.out.semaforo.mensaje;

import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;

import java.time.Instant;

/**
 * Qué mensaje del semáforo vio cada persona cada día ({@code mensajes_semaforo_elegidos}, D-277), por canal (Hoy o el
 * chat, D-281). Solo se agrega: una fila por persona, fecha local, canal y grupo, que nunca se actualiza ni se borra.
 */
public interface MensajesElegidosPort {

    /**
     * Lo guardado para esa clave, en UNA consulta: el mensaje ya elegido por ese canal para ese grupo y, solo si no hay,
     * lo que la persona vio en los 14 días anteriores (de cualquier canal y grupo) y lo que el otro canal le mostró
     * hoy. Con la elección hecha el resto no se trae.
     */
    EleccionesDelDia deLaPersona(ClaveDeLaEleccion clave);

    /**
     * Guarda la elección si todavía no hay una para esa clave, y devuelve el id que quedó guardado: el propio, o el de
     * quien llegó primero si dos lecturas se cruzaron. Es lo que hace que las dos terminen mostrando el mismo mensaje.
     */
    String registrar(ClaveDeLaEleccion clave, String mensajeId, Instant ahora);
}
