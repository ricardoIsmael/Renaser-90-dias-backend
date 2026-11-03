package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.ConsultarComunidadDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ComunidadDeLaFormacionFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La lectura de los tipos de comunidad de una formación para Administración (spec 024). El dato —qué tipos están prendidos— es del catálogo de
 * formaciones ({@code users}); lo que {@code chat} suma es cuántas conversaciones tiene cada tipo. Prender o apagar un tipo es de {@code users}.
 */
@Service
class ComunidadDeLaFormacionAdminService implements ConsultarComunidadDeLaFormacionUseCase {

    private final ComunidadDeLaFormacionFinder comunidades;
    private final LoadConversacionPort conversaciones;
    private final UserSummaryFinder usuarios;

    ComunidadDeLaFormacionAdminService(ComunidadDeLaFormacionFinder comunidades, LoadConversacionPort conversaciones,
                                       UserSummaryFinder usuarios) {
        this.comunidades = comunidades;
        this.conversaciones = conversaciones;
        this.usuarios = usuarios;
    }

    @Override
    public List<TipoDeLaComunidad> consultar(UserId actorId, Formacion formacion) {
        exigirAdministracion(actorId);
        Set<TipoDeComunidad> prendidos = comunidades.prendidosDe(formacion);
        Map<TipoConversacion, Long> conteo = conversaciones.contarPorTipo(formacion);
        return Arrays.stream(TipoDeComunidad.values())
                .map(tipo -> new TipoDeLaComunidad(tipo, prendidos.contains(tipo), cuantas(conteo, tipo)))
                .toList();
    }

    private static long cuantas(Map<TipoConversacion, Long> conteo, TipoDeComunidad tipo) {
        return conteo.entrySet().stream()
                .filter(e -> VisibilidadPorFormacion.tipoDeComunidadDe(e.getKey()) == tipo)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private void exigirAdministracion(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!actor.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista administran los tipos de comunidad");
        }
    }
}
