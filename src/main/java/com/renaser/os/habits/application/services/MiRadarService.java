package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mi Radar (spec 012, rehecha por la 019, D-292): encender, apagar y elegir por dia de la semana el modo y las veces, y
 * leer como esta con las horas que salen del Despertar y el Dormir de cada dia. Solo la propia persona y con el programa
 * andando, con la misma puerta que el resto del Codigo Renaser ({@code AccesoAlRadar}).
 *
 * <p>Hoy y lo pasado no se editan (FR-005): el pedido llega con los dias que se tocan y, si uno de ellos CAMBIA y no es
 * editable, es un 409. Repetir un dia con candado tal como esta no cambia nada y se acepta. «Encendido» no es un dia: rige
 * en el acto. No mira el dia del programa: la app ofrece Mi Radar desde el dia 8 y es el registro ({@code RadarService}) el
 * que decide si se puede responder. Nada de lo que se guarda se loguea.
 */
@Service
public class MiRadarService implements ConsultarMiRadarUseCase, ConfigurarMiRadarUseCase {

    static final String MENSAJE_DE_DIA_NO_EDITABLE = "Hoy y los días que pasaron no se pueden cambiar";

    private final AccesoAlRadar acceso;
    private final LoadMiRadarPort loadPort;
    private final SaveMiRadarPort savePort;
    private final AgendaDelRadar agenda;

    MiRadarService(AccesoAlRadar acceso, LoadMiRadarPort loadPort, SaveMiRadarPort savePort, AgendaDelRadar agenda) {
        this.acceso = acceso;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.agenda = agenda;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanDeMiRadar consultar(UserId actorId, UserId participanteId) {
        ProgresoParticipanteHabits progreso = acceso.propio(actorId, participanteId);
        return agenda.semana(participanteId, ZoneId.of(progreso.timezone()), loadPort.deParticipante(participanteId));
    }

    @Override
    @Transactional
    public PlanDeMiRadar configurar(ConfigurarMiRadarCommand command) {
        ProgresoParticipanteHabits progreso = acceso.propio(command.actorId(), command.participanteId());
        ZoneId zona = ZoneId.of(progreso.timezone());
        MiRadar guardado = loadPort.deParticipante(command.participanteId());
        List<DiaDeMiRadar> tocados = diasTocados(guardado, command.dias());
        MiRadar nuevo = guardado.conEncendido(command.encendido()).conDias(tocados);
        requireEditables(guardado, tocados, agenda.hoy(zona));
        PlanDeMiRadar plan = agenda.semana(command.participanteId(), zona, nuevo);
        requireQueCaben(command.dias(), plan);
        savePort.guardar(command.participanteId(), nuevo);
        return plan;
    }

    /** Cada dia que llega, aplicado sobre lo que ya tenia (validando dia, modo y veces); un dia repetido es un 400. */
    private static List<DiaDeMiRadar> diasTocados(MiRadar guardado, List<CambioDeDia> cambios) {
        Set<Integer> vistos = new HashSet<>();
        return cambios.stream().map(cambio -> {
            if (!vistos.add(cambio.dia())) {
                throw new IllegalArgumentException("No repitas el mismo día");
            }
            return guardado.dia(cambio.dia()).con(cambio.modo(), cambio.veces(), cambio.sinRadar());
        }).toList();
    }

    /** Solo cuentan los dias que de verdad cambian: repetir un dia con candado tal como esta es inofensivo. */
    private static void requireEditables(MiRadar guardado, List<DiaDeMiRadar> tocados, LocalDate hoy) {
        boolean cambiaUnoBloqueado = tocados.stream().anyMatch(dia ->
                !dia.equals(guardado.dia(dia.dia())) && !esEditable(dia.dia(), hoy));
        if (cambiaUnoBloqueado) {
            throw new IllegalStateException(MENSAJE_DE_DIA_NO_EDITABLE);
        }
    }

    private static boolean esEditable(int dia, LocalDate hoy) {
        return HorarioDelRadar.esEditable(DayOfWeek.of(dia), hoy);
    }

    /**
     * Las veces que la persona PIDIO no pueden pasar de las horas que caben ese dia; sin horario no hay tope que comparar.
     * Las que se ponen por defecto no se miran: se recortan solas a lo que cabe.
     */
    private static void requireQueCaben(List<CambioDeDia> cambios, PlanDeMiRadar plan) {
        for (CambioDeDia cambio : cambios) {
            DiaDelRadar dia = plan.dias().get(cambio.dia() - 1);
            if (cambio.veces() != null && dia.maximo() > 0 && cambio.veces() > dia.maximo()) {
                throw new IllegalArgumentException("Ese día caben " + dia.maximo() + " veces");
            }
        }
    }
}
