package com.renaser.os.chat.application.ports.out.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Los chats de soporte de quien atiende, de a una página y del más reciente al más viejo (D-249). Solo
 * los soportes donde esa persona participa: los mismos que hoy le muestra la lista completa.
 */
public interface SoportesPorActividadPort {

    /** Hasta {@code pedido.cuantos()} soportes, en orden; menos si no hay más. */
    List<SoporteConActividad> pagina(PedidoDeSoportes pedido);

    /** Cuántos hay en total con el mismo filtro y en cuántos le quedan mensajes sin leer. */
    ConteoDeSoportes contar(UserId quienAtiende, Set<String> soloClaves, Collection<String> formaciones);

    /**
     * @param soloClaves si no es {@code null}, solo los soportes de esos aprendices (el uuid de cada uno, que es el segundo tramo de su clave
     *                   {@code soporte:<uuid>[:FORMACION]}): así llega el resultado de una búsqueda por nombre. Vacío = ninguno.
     * @param desde      {@code null} para la primera página
     * @param formaciones los códigos de las formaciones cuyo soporte se lista (spec 024): el alcance de quien atiende, sin las que tienen el soporte
     *                   apagado. Nunca vacío (con ninguna se liga el código que no es de ninguna).
     */
    record PedidoDeSoportes(UserId quienAtiende, Set<String> soloClaves, CursorDeSoportes desde, int cuantos,
                            Collection<String> formaciones) {
    }

    record SoporteConActividad(ConversacionId id, Instant actividad) {
    }

    record ConteoDeSoportes(long total, long conNoLeidos) {
    }
}
