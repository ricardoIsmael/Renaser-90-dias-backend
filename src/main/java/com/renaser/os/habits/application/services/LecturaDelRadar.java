package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
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
    private final AgendaDelRadar agenda;

    LecturaDelRadar(LoadRegistroRadarPort registros, LoadMiRadarPort miRadar,
                    ConsultarProgresoParticipanteHabitsPort progreso, AgendaDelRadar agenda) {
        this.registros = registros;
        this.miRadar = miRadar;
        this.progreso = progreso;
        this.agenda = agenda;
    }

    HistorialRadarPage pagina(UserId participanteId, Instant cursor, int tamanoPagina) {
        List<RegistroRadar> pagina = registros.historialDeParticipante(participanteId, cursor, tamanoPagina);
        return new HistorialRadarPage(pagina, siguienteCursor(pagina, tamanoPagina), miRadarSiAplica(participanteId));
    }

    /**
     * Mi Radar de la persona con lo de HOY (su Despertar, su Dormir y las veces), o {@code null} mientras su dia del
     * programa es menor que 8 —en la primera semana no aplica, y decir «apagado» ahi seria decir algo que la persona nunca
     * decidio— o con la cuenta suspendida (el horario no se lee de una cuenta suspendida). Una sola consulta de horario.
     */
    private PlanDeMiRadar miRadarSiAplica(UserId participanteId) {
        ProgresoParticipanteHabits delParticipante = progreso.deParticipante(participanteId).orElse(null);
        if (delParticipante == null || !HorarioDelRadar.aplicaMiRadar(delParticipante.diaPrograma())
                || delParticipante.suspendido()) {
            return null;
        }
        MiRadar guardado = miRadar.deParticipante(participanteId);
        ZoneId zona = ZoneId.of(delParticipante.timezone());
        DiaDelRadar hoy = agenda.deHoy(participanteId, zona, guardado);
        return new PlanDeMiRadar(guardado.encendido(), hoy.fecha(), List.of(hoy));
    }

    /** Pagina llena => puede haber mas, misma heuristica que radar.ts:384-387 (sin conteo extra). */
    private static Instant siguienteCursor(List<RegistroRadar> pagina, int tamanoPagina) {
        if (pagina.size() < tamanoPagina) {
            return null;
        }
        return pagina.get(pagina.size() - 1).creadoEn();
    }
}
