package com.renaser.os.calendar.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Compone los contratos publicos en vez de consultar tablas ajenas (D-41): los participantes activos
 * salen de `users`, y el mentor que lidera la celula y las asignaciones vigentes, de `community`, que es
 * su dueno.
 *
 * <p><b>Por que el mentor va aparte:</b> un MENTOR no tiene fila en `participantes_programa` (el programa
 * de 90 dias es obligatorio solo para APRENDIZ), asi que consultar solo a `users` lo dejaria afuera y
 * dejaria de recibir los recordatorios de los eventos de su propia celula. Espejo de
 * {@code findActiveMembersInCell()} del repo viejo, que resolvia lo mismo con un OR.
 *
 * <p><b>Las asignaciones vigentes, ademas del puntero (E-363, 2026-09-27).</b>
 * {@code participantes_programa.celula_id} nombra un solo grupo, el principal: un aprendiz sumado a un
 * segundo grupo (D-139) no recibia los avisos de los eventos de ese grupo ni podia abrirlos. Ahora cuenta
 * tambien quien tiene asignacion vigente en {@code asignaciones_celula} (aprendices, su mentor y, en la
 * recepcion, sus guias), que es la fuente de verdad. El puntero se conserva: nadie que recibia un aviso
 * deja de recibirlo. Se sigue avisando solo a cuentas ACTIVAS, como antes.
 */
@Component
class ConsultarMiembrosCelulaCalendarPersistenceAdapter implements ConsultarMiembrosCelulaPort,
        ConsultarPertenenciaAGrupoPort {

    private final ParticipacionProgramaFinder participacionFinder;
    private final CelulaFinder celulaFinder;
    private final AcompanamientoFinder acompanamientoFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    ConsultarMiembrosCelulaCalendarPersistenceAdapter(ParticipacionProgramaFinder participacionFinder,
                                                       CelulaFinder celulaFinder,
                                                       AcompanamientoFinder acompanamientoFinder,
                                                       UserSummaryFinder userSummaryFinder, Clock clock) {
        this.participacionFinder = participacionFinder;
        this.celulaFinder = celulaFinder;
        this.acompanamientoFinder = acompanamientoFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    /** LinkedHashSet: sin duplicados y con orden estable, por si el mentor tambien participa. */
    @Override
    public List<UserId> miembrosActivos(UUID celulaId) {
        var destinatarios = new LinkedHashSet<>(participacionFinder.miembrosActivosDeCelula(celulaId));
        celulaFinder.mentorDe(celulaId).ifPresent(destinatarios::add);
        Set<UserId> porAsignacion = integrantesPorAsignacion(celulaId, clock.now());
        porAsignacion.removeAll(destinatarios);
        destinatarios.addAll(soloActivos(porAsignacion));
        return List.copyOf(destinatarios);
    }

    /** Cualquier funcion vigente, con el grupo operativo: la misma pregunta con que el chat del grupo decide
     * quien entra ({@link AcompanamientoFinder#esIntegranteVigente}). */
    @Override
    public boolean perteneceHoy(UUID celulaId, UserId usuarioId) {
        return acompanamientoFinder.esIntegranteVigente(celulaId, usuarioId, clock.now());
    }

    /** Aprendices y quienes acompañan (mentor, guias). El SOPORTE (ADMIN/ALCHEMIST cubriendo un grupo sin
     * mentor) queda afuera, igual que antes: el aviso es para quien esta en el grupo, no para el staff. */
    private Set<UserId> integrantesPorAsignacion(UUID celulaId, Instant ahora) {
        Set<UserId> integrantes = new LinkedHashSet<>(acompanamientoFinder.aprendicesVigentes(celulaId, ahora));
        integrantes.addAll(acompanamientoFinder.acompanantesVigentes(celulaId, ahora));
        return integrantes;
    }

    private List<UserId> soloActivos(Set<UserId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return userSummaryFinder.findByIds(ids).values().stream()
                .filter(usuario -> usuario.status() == UserStatus.ACTIVE)
                .map(UserSummary::id)
                .toList();
    }
}
