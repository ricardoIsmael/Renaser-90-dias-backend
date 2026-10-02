package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort.ConteoDeSoportes;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort.SoporteConActividad;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La sección «Soporte» de Tribu para quien atiende (D-249). Con 300 aprendices la lista entera de chats
 * del Admin era una fila por aprendiz; esta los trae de a una página, del más reciente al más viejo, y
 * busca en el servidor (no sobre lo ya descargado).
 *
 * <p>Cada fila es la MISMA que da {@code GET /chat/conversations} para un soporte (último mensaje por
 * índice, E-471; no leídos; nombre derivado, D-221), resuelta en lote para la página: una consulta por
 * dato, nunca una por fila.
 *
 * <p>Solo ADMIN y ALCHEMIST activos. El interceptor deja pasar a MENTOR, ADMIN y ALCHEMIST sin mirar
 * (A-1), así que el 403 de un mentor y el de una cuenta suspendida salen de acá.
 */
@Service
public class SoportesPaginadosService implements ListarSoportesUseCase {

    private final UserSummaryFinder usuarios;
    private final BusquedaDeUsuariosFinder busqueda;
    private final SoportesPorActividadPort soportes;
    private final LoadConversacionPort conversaciones;
    private final LoadMensajePort mensajes;
    private final ContarNoLeidosPort noLeidos;
    private final NombresDeLosChatsService nombres;

    public SoportesPaginadosService(UserSummaryFinder usuarios, BusquedaDeUsuariosFinder busqueda,
                                    SoportesPorActividadPort soportes, LoadConversacionPort conversaciones,
                                    LoadMensajePort mensajes, ContarNoLeidosPort noLeidos,
                                    NombresDeLosChatsService nombres) {
        this.usuarios = usuarios;
        this.busqueda = busqueda;
        this.soportes = soportes;
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.noLeidos = noLeidos;
        this.nombres = nombres;
    }

    @Override
    public PaginaDeSoportes listar(PedidoDeSoportes pedido) {
        exigirQueAtiendaSoporte(pedido.actorId());
        Set<String> claves = clavesQueCoinciden(pedido.texto());
        if (claves != null && claves.isEmpty()) {
            Long ninguno = pedido.desde() == null ? 0L : null;
            return new PaginaDeSoportes(List.of(), null, ninguno, ninguno);
        }
        int tamano = Math.clamp(pedido.tamano() <= 0 ? TAMANO_POR_DEFECTO : pedido.tamano(), 1, TAMANO_MAXIMO);
        List<SoporteConActividad> filas = soportes.pagina(new SoportesPorActividadPort.PedidoDeSoportes(
                pedido.actorId(), claves, pedido.desde(), tamano + 1));
        boolean hayMas = filas.size() > tamano;
        List<SoporteConActividad> deEstaPagina = hayMas ? filas.subList(0, tamano) : filas;
        ConteoDeSoportes conteo = pedido.desde() == null ? soportes.contar(pedido.actorId(), claves) : null;
        return new PaginaDeSoportes(resumenes(pedido.actorId(), deEstaPagina),
                hayMas ? cursorDespuesDe(deEstaPagina.getLast()) : null,
                conteo == null ? null : conteo.total(), conteo == null ? null : conteo.conNoLeidos());
    }

    /** {@code null} = sin búsqueda (todos); vacío = nadie coincide. */
    private Set<String> clavesQueCoinciden(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return busqueda.coincidenCon(texto).stream().map(Conversacion::claveSoporteDe)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Las filas de la página en su orden, con sus datos en lote (como la lista completa). */
    private List<ConversacionResumen> resumenes(UserId actorId, List<SoporteConActividad> filas) {
        List<ConversacionId> ids = filas.stream().map(SoporteConActividad::id).toList();
        Map<ConversacionId, Conversacion> porId = conversaciones.porIds(ids).stream()
                .collect(Collectors.toMap(Conversacion::id, Function.identity()));
        Map<ConversacionId, Mensaje> ultimos = mensajes.ultimosPorConversacion(ids);
        Map<ConversacionId, Long> sinLeer = noLeidos.contarNoLeidos(actorId, ids);
        Map<ConversacionId, String> nombresDeLaPagina = nombres.nombresDe(porId.values());
        return ids.stream().map(porId::get).filter(Objects::nonNull)
                .map(c -> new ConversacionResumen(c, ultimos.get(c.id()), sinLeer.getOrDefault(c.id(), 0L),
                        null, null, null, null, nombresDeLaPagina.get(c.id())))
                .toList();
    }

    private static CursorDeSoportes cursorDespuesDe(SoporteConActividad ultima) {
        return new CursorDeSoportes(ultima.actividad(), ultima.id());
    }

    /** @throws NotAuthorizedException (403) si la cuenta no está activa o no es ADMIN/ALCHEMIST */
    private void exigirQueAtiendaSoporte(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!actor.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista ven todos los chats de soporte");
        }
    }
}
