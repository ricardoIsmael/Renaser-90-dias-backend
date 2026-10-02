package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * La sección «Soporte» de Tribu para quien atiende (D-249): los chats de soporte de a una página, del más
 * reciente al más viejo, con búsqueda por el nombre o el correo del aprendiz. Solo ADMIN y ALCHEMIST
 * activos (los que están en todos los soportes, D-136); cualquier otro, 403.
 */
public interface ListarSoportesUseCase {

    int TAMANO_POR_DEFECTO = 25;
    int TAMANO_MAXIMO = 50;

    PaginaDeSoportes listar(PedidoDeSoportes pedido);

    /**
     * @param texto  lo escrito en el buscador; {@code null} o en blanco = todos
     * @param desde  {@code null} para la primera página
     * @param tamano se acota a 1..{@link #TAMANO_MAXIMO}
     */
    record PedidoDeSoportes(UserId actorId, String texto, CursorDeSoportes desde, int tamano) {
    }

    /**
     * @param siguiente   {@code null} si no hay más
     * @param total       cuántos coinciden en total; solo en la primera página ({@code null} en las demás)
     * @param conNoLeidos en cuántos de ellos hay mensajes sin leer; solo en la primera página
     */
    record PaginaDeSoportes(List<ConversacionResumen> conversaciones, CursorDeSoportes siguiente,
                            Long total, Long conNoLeidos) {
    }
}
