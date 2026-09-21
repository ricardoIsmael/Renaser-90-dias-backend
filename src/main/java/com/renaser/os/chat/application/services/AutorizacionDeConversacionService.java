package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa la regla descrita en {@link AutorizarAccesoAConversacionUseCase}.
 *
 * <p>Es la misma decision que toman {@code MensajeService.requireParticipante},
 * {@code ConversacionService} y {@code PresenciaService}, con las mismas ramas y en el mismo
 * orden. La diferencia es que aquella lanza y esta responde: el interceptor del WebSocket necesita
 * decidir sin excepciones de dominio, y el reparto de presencia necesita <i>filtrar</i> una lista.
 *
 * <p><b>Son cuatro copias y siguen siendolo</b> (auditoria de seguridad): la rama de SOPORTE se
 * agrego a las cuatro a la vez justamente porque arreglar dos deja la mitad de la superficie
 * abierta. Si se toca una, se tocan las cuatro — o mejor, se termina el trabajo que este puerto
 * declara y las otras tres pasan a delegar aca en vez de copiarlo.
 */
@Service
public class AutorizacionDeConversacionService implements AutorizarAccesoAConversacionUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final UserSummaryFinder userSummaryFinder;

    public AutorizacionDeConversacionService(LoadConversacionPort loadConversacionPort,
                                      EsParticipantePort esParticipantePort,
                                      PertenenciaVigentePort pertenenciaVigentePort,
                                      UserSummaryFinder userSummaryFinder) {
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean puedeVer(ConversacionId conversacionId, UserId usuarioId) {
        return loadConversacionPort.porId(conversacionId)
                .map(conversacion -> autorizado(conversacion, usuarioId))
                .orElse(false);
    }

    private boolean autorizado(Conversacion conversacion, UserId usuarioId) {
        if (conversacion.tipo() == TipoConversacion.CELULA) {
            /* Para un grupo, la proyeccion `participantes_conversacion` no sirve como fuente: es
               eventual, y una proyeccion vieja no se limita a mostrar de menos — CONCEDE DE MAS.
               Un mentor que roto el mes pasado conservaria su fila, y con ella la puerta abierta
               al chat de gente que ya no acompana. La pertenencia vigente, en cambio, exige que
               el grupo siga operativo y que la asignacion siga viva, y por eso revoca en el acto
               sin depender de que ningun barrido haya corrido. */
            return pertenenciaVigentePort.perteneceAlGrupo(conversacion.celulaId(), usuarioId);
        }
        if (!esParticipantePort.esParticipante(conversacion.id(), usuarioId)) {
            return false;
        }
        /* Un SOPORTE tambien se gana por ROL —el aprendiz y los ADMIN/ALCHEMIST activos, nadie
           mas— y no por ser una de las dos partes como una DIRECTA, asi que le cabe exactamente el
           mismo argumento de arriba: la fila no alcanza. Sin esto, un ex administrador degradado
           conservaba la suscripcion STOMP al chat de soporte de cada aprendiz y lo seguia
           recibiendo en vivo. Vale aunque la revocacion del listener no haya corrido todavia. */
        return !conversacion.seGanaPorRolDeStaff(usuarioId) || esStaffAdministrativo(usuarioId);
    }

    /**
     * El rol de AHORA, no el que dejo escrita la proyeccion. {@code canManageRoles()} es
     * exactamente {ADMIN, ALCHEMIST}, el mismo conjunto que
     * {@code ConversacionSoporteService.STAFF_ADMINISTRATIVO}. Un usuario que ya no existe no es
     * staff: falla cerrado.
     */
    private boolean esStaffAdministrativo(UserId usuarioId) {
        return userSummaryFinder.findById(usuarioId)
                .map(usuario -> usuario.role().canManageRoles())
                .orElse(false);
    }
}
