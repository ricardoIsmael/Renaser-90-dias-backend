package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.HabitoLogrosFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.RadarLogrosFinder;
import com.renaser.os.users.api.RocaLogrosFinder;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Gap #22 (docs/PLAN_INTEGRACION_FRONTEND.md §5). Compone {@code Logros} agregando finders
 * publicos de 3 modulos ajenos (`habits`, `rocks`) mas la participacion propia de `users` —
 * ninguno de los campos es dominio de `points` (ver javadoc de {@link RocaLogrosFinder}).
 *
 * <p>Clase separada de {@link ParticipacionProgramaService} a proposito: no es un caso de
 * uso del agregado {@code ParticipacionPrograma}, es una composicion de lectura pura sobre
 * varios modulos — mezclarla ahi habria forzado inyectar los 3 finders ajenos en un servicio
 * que hoy no conoce nada fuera de `users`.
 */
@Service
class LogrosService implements GetLogrosUseCase {

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final HabitoLogrosFinder habitoLogrosFinder;
    private final RadarLogrosFinder radarLogrosFinder;
    private final RocaLogrosFinder rocaLogrosFinder;

    LogrosService(RequireActiveUserGuard requireActiveUserGuard,
                  ParticipacionProgramaFinder participacionProgramaFinder, HabitoLogrosFinder habitoLogrosFinder,
                  RadarLogrosFinder radarLogrosFinder, RocaLogrosFinder rocaLogrosFinder) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.habitoLogrosFinder = habitoLogrosFinder;
        this.radarLogrosFinder = radarLogrosFinder;
        this.rocaLogrosFinder = rocaLogrosFinder;
    }

    /**
     * 404 si el actor no esta inscripto en el programa — mismo caso que el backend viejo
     * ({@code 'Trainee profile not found'}, code 404): los logros son un concepto de
     * aprendiz, un staff sin participacion no tiene nada que mostrar aca.
     */
    @Override
    public Logros getLogros(GetLogrosQuery query) {
        User actor = requireActiveUserGuard.of(query.actorId());
        UserId actorId = actor.id();

        var participacion = participacionProgramaFinder.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (!participacion.inscrito()) {
            throw new NoSuchElementException("El actor no esta inscripto en el programa: " + actorId);
        }

        return new Logros(participacion.diaPrograma(), null, habitoLogrosFinder.totalHabitosCompletados(actorId),
                habitoLogrosFinder.primerHabitoCompletadoEn(actorId).orElse(null),
                rocaLogrosFinder.totalRocksCompleted(actorId), rocaLogrosFinder.firstRockCompletedAt(actorId)
                        .orElse(null), rocaLogrosFinder.bestRocksStreakDays(actorId),
                radarLogrosFinder.totalRegistrosRadar(actorId),
                radarLogrosFinder.primerRegistroRadarEn(actorId).orElse(null));
    }
}
