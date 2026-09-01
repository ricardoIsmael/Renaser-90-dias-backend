package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.miembro.ListarDirectorioMiembrosUseCase;
import com.renaser.os.chat.application.ports.in.miembro.ListarMiembrosGlobalUseCase;
import com.renaser.os.chat.application.ports.in.miembro.MiembroResumen;
import com.renaser.os.chat.application.ports.in.miembro.PaginaMiembros;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Directorio de usuarios (#27) y roster del grupo GLOBAL (#28). Ambos casos de uso se
 * apoyan en {@link ListarUsuariosDeConversacionPort} sobre la conversacion GLOBAL — todo
 * usuario activo es participante ahi por auto-join (V1__baseline_renaser.sql:1293-1295) —
 * y resuelven nombre/avatar/rol via {@code UserSummaryFinder} EN LOTE, nunca una consulta
 * por miembro.
 *
 * <p>La paginacion y el filtro por nombre se resuelven en memoria sobre la lista ya
 * resuelta: no hay forma de empujar el filtro a SQL sin cruzar el limite del modulo hacia
 * la tabla `usuarios` de `users` (CLAUDE.MD sec. 5.1 — solo `users.api` es visible desde
 * aca). Para el tamano de comunidad de este producto es aceptable; si la base de usuarios
 * activos crece a un punto donde esto pese, la solucion es una nueva capacidad de
 * busqueda en `users.api` (`users` es otro modulo, fuera de este encargo).
 */
@Service
public class MiembroService implements ListarDirectorioMiembrosUseCase, ListarMiembrosGlobalUseCase {

    private static final int LIMITE_POR_DEFECTO = 30;
    private static final int LIMITE_MAXIMO = 100;

    private static final Comparator<MiembroResumen> POR_NOMBRE = Comparator
            .comparing((MiembroResumen m) -> m.nombreCompleto() == null ? "" : m.nombreCompleto().toLowerCase(Locale.ROOT))
            .thenComparing(m -> m.id().value());

    private final LoadConversacionPort loadConversacionPort;
    private final ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final UserSummaryFinder userSummaryFinder;

    public MiembroService(LoadConversacionPort loadConversacionPort,
                           ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort,
                           EsParticipantePort esParticipantePort, UserSummaryFinder userSummaryFinder) {
        this.loadConversacionPort = loadConversacionPort;
        this.listarUsuariosDeConversacionPort = listarUsuariosDeConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public PaginaMiembros listar(UserId actorId, String query, UserId cursor, int limite) {
        requireActivo(actorId);
        Conversacion global = requireGlobal();

        List<MiembroResumen> candidatos = destinatariosPosiblesDeUnDmNuevo(global, actorId).stream()
                .filter(m -> coincideConLaBusqueda(m, query))
                .sorted(POR_NOMBRE)
                .toList();
        return paginar(candidatos, cursor, limite);
    }

    @Override
    public PaginaMiembros listar(UserId actorId, UserId cursor, int limite) {
        requireActivo(actorId);
        Conversacion global = requireGlobal();
        requireParticipante(global, actorId);

        // Roster informativo ("quien es miembro"), no un directorio para escribir:
        // a proposito NO filtra por UserStatus (a diferencia de la sobrecarga de arriba).
        List<MiembroResumen> miembros = resolverParticipantesDe(global).stream()
                .map(MiembroService::aResumen)
                .sorted(POR_NOMBRE)
                .toList();
        return paginar(miembros, cursor, limite);
    }

    /** Quienes pueden ser el otro lado de un DM que {@code actorId} abre hoy: miembros de
     * GLOBAL que siguen habilitados para recibirlo, ya proyectados a {@link MiembroResumen}.
     * Es la regla de "a quien se le puede escribir" y nada mas — la busqueda por nombre, el
     * orden y la paginacion son del caso de uso, no de esta regla. */
    private List<MiembroResumen> destinatariosPosiblesDeUnDmNuevo(Conversacion global, UserId actorId) {
        return resolverParticipantesDe(global).stream()
                .filter(u -> u.status() == UserStatus.ACTIVE)  // SUSPENDED no sirve como destino de un DM nuevo
                .filter(u -> !u.id().equals(actorId))           // nunca mandarse un DM a uno mismo
                .map(MiembroService::aResumen)
                .toList();
    }

    /** Todos los participantes de GLOBAL, resueltos en, como mucho, dos consultas EN
     * LOTE (participantes + `users.api`) — nunca una por miembro. Devuelve
     * {@code UserSummary} crudo (no {@link MiembroResumen}) para que cada caller decida
     * si filtra por {@code UserStatus} antes de proyectar. */
    private Collection<UserSummary> resolverParticipantesDe(Conversacion global) {
        List<UserId> ids = listarUsuariosDeConversacionPort.usuariosDe(global.id());
        if (ids.isEmpty()) {
            return List.of();
        }
        return userSummaryFinder.findByIds(ids).values();
    }

    private static MiembroResumen aResumen(UserSummary u) {
        return new MiembroResumen(u.id(), u.fullName(), u.avatarUrl(), u.role());
    }

    private static boolean coincideConLaBusqueda(MiembroResumen m, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String nombre = m.nombreCompleto() == null ? "" : m.nombreCompleto().toLowerCase(Locale.ROOT);
        return nombre.contains(query.strip().toLowerCase(Locale.ROOT));
    }

    private PaginaMiembros paginar(List<MiembroResumen> ordenados, UserId cursor, int limite) {
        int limiteEfectivo = limite <= 0 ? LIMITE_POR_DEFECTO : Math.min(limite, LIMITE_MAXIMO);
        int desde = cursor == null ? 0 : indiceSiguienteA(ordenados, cursor);
        List<MiembroResumen> resto = ordenados.subList(Math.min(desde, ordenados.size()), ordenados.size());
        boolean hayMas = resto.size() > limiteEfectivo;
        List<MiembroResumen> pagina = hayMas ? resto.subList(0, limiteEfectivo) : resto;
        UserId siguienteCursor = hayMas ? pagina.get(pagina.size() - 1).id() : null;
        return new PaginaMiembros(pagina, siguienteCursor, hayMas);
    }

    /** Si el cursor de una pagina anterior ya no aparece (dato cambio entre llamadas,
     * o la busqueda cambio), se retoma desde el principio — nunca se rompe con un error
     * por un cursor "stale" (comentario del propio frontend: una busqueda nueva no
     * combina con el cursor de una anterior). */
    private static int indiceSiguienteA(List<MiembroResumen> ordenados, UserId cursor) {
        for (int i = 0; i < ordenados.size(); i++) {
            if (ordenados.get(i).id().equals(cursor)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void requireParticipante(Conversacion conversacion, UserId usuarioId) {
        if (!esParticipantePort.esParticipante(conversacion.id(), usuarioId)) {
            throw new NotAuthorizedException("No sos participante de esta conversacion");
        }
    }

    private Conversacion requireGlobal() {
        return loadConversacionPort.global()
                .orElseThrow(() -> new NoSuchElementException("La conversacion GLOBAL todavia no existe"));
    }

    private void requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
