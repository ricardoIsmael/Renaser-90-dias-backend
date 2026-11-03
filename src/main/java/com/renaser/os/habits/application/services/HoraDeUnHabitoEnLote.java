package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorariosDelHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * La hora que VARIAS personas tienen un dia para UN habito, resuelta en lote (auditoria de consultas del 2026-11-02, H-01).
 *
 * <p><b>Por que existe.</b> «Habitos de todos → personas de un habito» mostraba la hora planificada de cada fila llamando, por cada persona de la pagina, al
 * caso de uso completo de preferencias (catalogo, audiencias, historial de rol, horarios, preferencias, cambios, semana, fechas propias, cuota, apagados y
 * desbloqueos): unas 20 consultas por fila, 630 por pagina de 30 y 2.100 por una de 100. Aqui se contesta la unica pregunta que esa lista hace, con una
 * consulta por capa para TODAS las filas: el progreso de las personas, el habito, los horarios del catalogo, las capas de preferencia (general, cambio
 * general, dia de la semana y fecha) y, solo si hace falta, el dia mas alto ya generado.
 *
 * <p><b>La precedencia no se reescribe.</b> Las capas las resuelve {@code LoadPreferenciaHorarioPort} con las mismas funciones que la lectura de una sola
 * persona, y la hora del catalogo sale de {@link ConsultaPreferenciasHorarioService#vigenteDeCatalogo}, la misma de esa lectura: la preferencia gana si
 * existe y, si no, rige el horario del catalogo para el dia de programa de ESA fecha en la zona de ESA persona.
 *
 * <p><b>Quien llama ya establecio que el habito le aplica a esas personas</b> (la lista parte de sus registros del dia): no se vuelve a filtrar por
 * audiencia de rol. Una persona suspendida o sin programa no tiene hora (antes era la excepcion de {@code deFecha}).
 */
@Component
public class HoraDeUnHabitoEnLote {

    private static final HorariosDelHabito SIN_HORARIOS = HorariosDelHabito.de(List.of());

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final Clock clock;

    public HoraDeUnHabitoEnLote(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                                LoadHorarioHabitoPort loadHorarioPort, LoadPreferenciaHorarioPort loadPreferenciaPort,
                                LoadRegistroHabitoPort loadRegistroPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadRegistroPort = loadRegistroPort;
        this.clock = clock;
    }

    /**
     * @return la hora de disparo de cada persona ese dia; sin entrada = no tiene hora (suspendida, sin programa, o ni ella ni el catalogo le fijan una)
     */
    @Transactional(readOnly = true)
    public Map<UserId, LocalTime> de(java.util.Collection<UserId> personas, HabitoId habitoId, LocalDate fecha) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        Optional<Habito> habito = loadHabitoPort.byId(habitoId);
        if (habito.isEmpty()) {
            return Map.of();
        }
        Map<UserId, ProgresoParticipanteHabits> progresos = progresoPort.deVarios(personas);
        List<UserId> vigentes = progresos.entrySet().stream().filter(e -> !e.getValue().suspendido()).map(Map.Entry::getKey).toList();
        if (vigentes.isEmpty()) {
            return Map.of();
        }
        HorariosDelHabito catalogo = HorariosDelHabito.porHabito(List.of(habito.get()), loadHorarioPort.porHabitos(Set.of(habitoId)))
                .getOrDefault(habitoId, SIN_HORARIOS);
        Map<UserId, PreferenciaHorario> preferencias = loadPreferenciaPort.horaDeUnHabitoDeVarios(vigentes, habitoId, fecha);
        TipoDia tipoDia = TipoDia.delDia(fecha);
        Map<UserId, Integer> diasDelPrograma = new HashMap<>();
        Set<UserId> necesitanSaberSiYaCorrio = new HashSet<>();
        for (UserId persona : vigentes) {
            int dia = diaDelProgramaEn(progresos.get(persona), fecha);
            diasDelPrograma.put(persona, dia);
            if (catalogo.necesitaSaberSiYaCorrio(dia, tipoDia)) {
                necesitanSaberSiYaCorrio.add(persona);
            }
        }
        // Solo si algun caso lo necesita (D-200) y UNA consulta para todos.
        Map<UserId, Integer> yaGenerados = necesitanSaberSiYaCorrio.isEmpty() ? Map.of()
                : loadRegistroPort.diaProgramaMasAltoGeneradoDeVarios(necesitanSaberSiYaCorrio, habitoId);

        Map<UserId, LocalTime> horas = new HashMap<>();
        for (UserId persona : vigentes) {
            HorarioHabito delCatalogo = ConsultaPreferenciasHorarioService.vigenteDeCatalogo(catalogo, diasDelPrograma.get(persona), tipoDia,
                    yaGenerados.get(persona));
            PreferenciaHorario preferencia = preferencias.get(persona);
            LocalTime hora = preferencia != null && preferencia.horaDisparo() != null ? preferencia.horaDisparo()
                    : delCatalogo != null ? delCatalogo.horaDisparo() : null;
            if (hora != null) {
                horas.put(persona, hora);
            }
        }
        return horas;
    }

    /** El dia de programa que le corresponde a esa fecha: el de hoy mas la distancia en dias, contados en SU zona (la misma cuenta de {@code deFecha}). */
    private int diaDelProgramaEn(ProgresoParticipanteHabits progreso, LocalDate fecha) {
        LocalDate hoy = clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate();
        return Math.toIntExact(progreso.diaPrograma() + ChronoUnit.DAYS.between(hoy, fecha));
    }
}
