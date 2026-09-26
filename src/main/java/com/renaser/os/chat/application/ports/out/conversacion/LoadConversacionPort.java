package com.renaser.os.chat.application.ports.out.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LoadConversacionPort {

    Optional<Conversacion> porId(ConversacionId id);

    Optional<Conversacion> porClaveDirecta(String claveDirecta);

    /**
     * Cuales de estas claves ya tienen conversacion, EN UNA consulta. Existe para abrir en bloque
     * los chats de dos de un grupo (D-173) sin una consulta por pareja: la recepcion no tiene
     * tope y cada ingreso reconcilia el grupo entero.
     */
    Set<String> clavesDirectasExistentes(Collection<String> claves);

    Optional<Conversacion> porCelulaId(UUID celulaId);

    Optional<Conversacion> global();

    /**
     * Todas las conversaciones de soporte, EN UNA consulta (D-136).
     *
     * <p>Existe para los dos unicos usos que las necesitan en bloque y que sin esto serian un N+1
     * (D-43): sumar a un ADMIN/ALCHEMIST nuevo a las que ya hay, y saber de una sola vez cuales de
     * los aprendices del padron ya tienen la suya durante el relleno.
     *
     * <p>Sin paginar a proposito: hay exactamente una por aprendiz del padron (25 al 2026-09-16), y
     * quien llama necesita el conjunto entero para comparar contra el padron entero. Si el padron
     * creciera a miles, este es el metodo que hay que paginar.
     */
    List<Conversacion> deSoporte();

    /** Todas las conversaciones donde {@code usuarioId} es participante. */
    List<Conversacion> misConversaciones(UserId usuarioId);
}
