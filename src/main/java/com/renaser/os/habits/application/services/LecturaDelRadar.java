package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Una pagina del historial del Codigo Renaser con Mi Radar de la persona, armada UNA vez para quien la lee: ella
 * ({@code RadarService}), su mentor ({@code AcompanamientoDeAlumnoService}) y el lider, la administracion y el
 * alquimista ({@code CodigoRenaserDePersonaService}). Cada uno autoriza a su manera; todos cortan la pagina igual.
 * No autoriza nada.
 */
@Component
class LecturaDelRadar {

    private final LoadRegistroRadarPort registros;
    private final LoadMiRadarPort miRadar;
    private final ConsultarProgresoParticipanteHabitsPort progreso;

    LecturaDelRadar(LoadRegistroRadarPort registros, LoadMiRadarPort miRadar,
                    ConsultarProgresoParticipanteHabitsPort progreso) {
        this.registros = registros;
        this.miRadar = miRadar;
        this.progreso = progreso;
    }

    HistorialRadarPage pagina(UserId participanteId, Instant cursor, int tamanoPagina) {
        List<RegistroRadar> pagina = registros.historialDeParticipante(participanteId, cursor, tamanoPagina);
        return new HistorialRadarPage(pagina, siguienteCursor(pagina, tamanoPagina), miRadarSiAplica(participanteId));
    }

    /**
     * Mi Radar de la persona, o {@code null} mientras su dia del programa es menor que 8: en la primera semana no
     * aplica, y decir «apagado» ahi seria decir algo que la persona nunca decidio. Ni siquiera se lee la columna.
     */
    private MiRadar miRadarSiAplica(UserId participanteId) {
        boolean aplica = progreso.deParticipante(participanteId)
                .map(p -> HorarioDelRadar.aplicaMiRadar(p.diaPrograma())).orElse(false);
        return aplica ? miRadar.deParticipante(participanteId) : null;
    }

    /** Pagina llena => puede haber mas, misma heuristica que radar.ts:384-387 (sin conteo extra). */
    private static Instant siguienteCursor(List<RegistroRadar> pagina, int tamanoPagina) {
        if (pagina.size() < tamanoPagina) {
            return null;
        }
        return pagina.get(pagina.size() - 1).creadoEn();
    }
}
