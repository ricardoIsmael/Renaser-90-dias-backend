package com.renaser.os.rocks.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41). `rocks` necesita ademas
 * el Día 1 elegido y la zona real del participante para calcular sus ventanas de
 * planificacion — ambas viajan en la proyeccion publica.
 *
 * <p><b>Semantica preservada:</b> la query anterior era INNER JOIN — sin fila de
 * participante, vacio.
 *
 * <p><b>La fecha del día 90, solo desde el día 90 (D-203).</b> La semana de rocas se ancla en el primer
 * día efectivo del programa, y {@code rocks} lo reconstruye como {@code hoy − (día − 1)} con el día que
 * ya da {@code users.api}. Esa cuenta deja de servir cuando el día llega acotado a 90 (el último día y
 * todo lo que sigue a la graduación): ahí se pide a {@link ProgramasActivadosFinder} la fecha real del
 * día 90, que calcula el agregado de {@code users} con el ajuste. Antes del 90 no se pide: sería una
 * consulta más en cada lectura de rocas (el tablero lee el progreso cinco veces) para obtener la misma
 * fecha que ya da la cuenta.
 */
@Component
class ConsultarProgresoParticipanteRocksPersistenceAdapter implements ConsultarProgresoParticipanteRocksPort {

    private final ParticipacionProgramaFinder participacionFinder;
    private final ProgramasActivadosFinder programasActivadosFinder;

    ConsultarProgresoParticipanteRocksPersistenceAdapter(ParticipacionProgramaFinder participacionFinder,
                                                         ProgramasActivadosFinder programasActivadosFinder) {
        this.participacionFinder = participacionFinder;
        this.programasActivadosFinder = programasActivadosFinder;
    }

    @Override
    public Optional<ProgresoParticipanteRocks> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .filter(ParticipacionPrograma::inscrito)
                .map(this::aProgreso);
    }

    /**
     * Viaja el Día 1 ELEGIDO, no {@code fecha_inicio}: antes de activar, esa columna guarda una fecha
     * provisional del alta y anclar la semana ahí inventaba fechas (D-201, D-203).
     */
    private ProgresoParticipanteRocks aProgreso(ParticipacionPrograma participacion) {
        return new ProgresoParticipanteRocks(participacion.diaPrograma(), participacion.diaUnoElegido(),
                participacion.zona(), mapearRol(participacion.rol()), participacion.suspendido(),
                participacion.activado(), ultimaFechaSiElDiaVieneAcotado(participacion));
    }

    private LocalDate ultimaFechaSiElDiaVieneAcotado(ParticipacionPrograma participacion) {
        if (!participacion.activado() || participacion.diaPrograma() < SemanaPrograma.ULTIMO_DIA) {
            return null;
        }
        return programasActivadosFinder.de(participacion.participanteId())
                .map(ProgramaActivado::ultimaFecha)
                .orElse(null);
    }

    private static RolParticipante mapearRol(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> RolParticipante.ALCHEMIST;
            case ADMIN -> RolParticipante.ADMIN;
            case MENTOR_LEAD -> RolParticipante.MENTOR_LEAD;
            case MENTOR -> RolParticipante.MENTOR;
            case TRAINEE -> RolParticipante.TRAINEE;
        };
    }
}
