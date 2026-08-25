package com.renaser.os.calendar.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Compone los dos contratos publicos en vez de consultar tablas ajenas (D-41): los
 * participantes activos salen de `users` y el mentor que lidera la celula, de
 * `community`, que es su dueno.
 *
 * <p><b>Por que el mentor va aparte:</b> un MENTOR no tiene fila en
 * `participantes_programa` (el programa de 90 dias es obligatorio solo para APRENDIZ),
 * asi que consultar solo a `users` lo dejaria afuera y dejaria de recibir los
 * recordatorios de los eventos de su propia celula. Espejo de
 * {@code findActiveMembersInCell()} del repo viejo, que resolvia lo mismo con un OR.
 */
@Component
class ConsultarMiembrosCelulaCalendarPersistenceAdapter implements ConsultarMiembrosCelulaPort {

    private final ParticipacionProgramaFinder participacionFinder;
    private final CelulaFinder celulaFinder;

    ConsultarMiembrosCelulaCalendarPersistenceAdapter(ParticipacionProgramaFinder participacionFinder,
                                                       CelulaFinder celulaFinder) {
        this.participacionFinder = participacionFinder;
        this.celulaFinder = celulaFinder;
    }

    /** LinkedHashSet: sin duplicados y con orden estable, por si el mentor tambien participa. */
    @Override
    public List<UserId> miembrosActivos(UUID celulaId) {
        var destinatarios = new LinkedHashSet<>(participacionFinder.miembrosActivosDeCelula(celulaId));
        celulaFinder.mentorDe(celulaId).ifPresent(destinatarios::add);
        return List.copyOf(destinatarios);
    }
}
