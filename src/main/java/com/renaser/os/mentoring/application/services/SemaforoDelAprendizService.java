package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizAdministrativoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizUseCase;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * El detalle del semáforo de una persona, visto por su mentor o por administración
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1).
 *
 * <p>El detalle en sí es el de {@code points} tal cual ({@link SemaforoFinder#detalleDe}): el mismo
 * que la persona ve de sí misma en {@code /me/semaforo}. Acá solo se decide QUIÉN puede pedirlo, con
 * dos guards distintos que no se parametrizan entre sí.
 */
@Service
public class SemaforoDelAprendizService implements ConsultarSemaforoDelAprendizUseCase,
        ConsultarSemaforoDelAprendizAdministrativoUseCase {

    private final AccesoAVistasDelSemaforo acceso;
    private final SemaforoFinder semaforoFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    SemaforoDelAprendizService(AccesoAVistasDelSemaforo acceso, SemaforoFinder semaforoFinder,
                               UserSummaryFinder userSummaryFinder, Clock clock) {
        this.acceso = acceso;
        this.semaforoFinder = semaforoFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleDelSemaforo detalleDe(ConsultaDetalleDelAprendiz consulta) {
        Instant ahora = clock.now();
        UserId aprendiz = UserId.of(consulta.aprendizId());
        acceso.requireAcompananteVigente(consulta.actorId(), consulta.grupoId(), ahora);
        // Y ademas que el aprendiz sea de ESE grupo (V12): si no, bastaria el id de un grupo propio.
        acceso.requireAprendizDelGrupo(aprendiz, consulta.grupoId(), ahora);
        return semaforoFinder.detalleDe(aprendiz, consulta.semanas());
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleDelSemaforo detalleDe(ConsultaDetalleAdministrativa consulta) {
        acceso.requireAdminActivo(consulta.actorId());
        UserId persona = UserId.of(consulta.aprendizId());
        requireExiste(persona);
        return semaforoFinder.detalleDe(persona, consulta.semanas());
    }

    /**
     * Un id que no es de nadie es 404, como en la semana administrativa: sin esto la respuesta sería
     * un «no aplica» que haría creer que la persona existe y no tiene programa.
     */
    private void requireExiste(UserId persona) {
        if (userSummaryFinder.findById(persona).isEmpty()) {
            throw new NoSuchElementException("Persona no encontrada");
        }
    }
}
