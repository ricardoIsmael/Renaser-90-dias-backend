package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.CuotaEdicionHorario;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contraparte de lectura de {@link PreferenciaHorarioService} (E-55): que rige hoy, que quedo
 * programado y cuanto cupo semanal queda. El conjunto de habitos es el mismo que usa
 * {@code RegistroService.generar} — catalogo activo + personales activos del propio aprendiz.
 *
 * <p><b>Nunca N+1:</b> horarios, preferencias y cambios programados se piden UNA vez cada uno
 * por el conjunto completo de habitos, no una consulta por habito.
 */
@Service
public class ConsultaPreferenciasHorarioService implements ConsultarPreferenciasHorarioUseCase {

    /** El programa arranca en el dia 1; el dia 0 es "todavia no activo". */
    private static final int PRIMER_DIA_DEL_PROGRAMA = 1;

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadCambioHorarioPendientePort loadCambioPendientePort;
    private final HistorialCambioHorarioPort historialPort;
    private final Clock clock;

    public ConsultaPreferenciasHorarioService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                               LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                               LoadPreferenciaHorarioPort loadPreferenciaPort,
                                               LoadCambioHorarioPendientePort loadCambioPendientePort,
                                               HistorialCambioHorarioPort historialPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadCambioPendientePort = loadCambioPendientePort;
        this.historialPort = historialPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPreferenciasHorario consultar(UserId actorId) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate();
        List<Habito> habitos = habitosActivosDe(actorId);
        return new ResumenPreferenciasHorario(horariosDe(actorId, habitos, hoy, progreso.diaPrograma()),
                cuotaDe(actorId, hoy, progreso.diaPrograma()));
    }

    /** Mismo criterio que {@code RegistroService.generar}: catalogo activo + personales del aprendiz. */
    private List<Habito> habitosActivosDe(UserId actorId) {
        List<Habito> habitos = new ArrayList<>(loadHabitoPort.catalogoActivo());
        habitos.addAll(loadHabitoPort.personalesActivosDe(actorId));
        return habitos;
    }

    private List<HorarioDeHabito> horariosDe(UserId actorId, List<Habito> habitos, LocalDate hoy, int diaPrograma) {
        if (habitos.isEmpty()) {
            return List.of();
        }
        Set<HabitoId> ids = habitos.stream().map(Habito::id).collect(Collectors.toSet());
        Map<HabitoId, List<HorarioHabito>> horarios = loadHorarioPort.porHabitos(ids).stream()
                .collect(Collectors.groupingBy(HorarioHabito::habitoId));
        Map<HabitoId, PreferenciaHorario> preferencias = loadPreferenciaPort.porParticipanteYHabitos(actorId, ids)
                .stream().collect(Collectors.toMap(PreferenciaHorario::habitoId, p -> p));
        Map<HabitoId, CambioHorarioPendiente> programados = loadCambioPendientePort.deParticipante(actorId).stream()
                .collect(Collectors.toMap(CambioHorarioPendiente::habitoId, c -> c));
        TipoDia tipoDia = TipoDia.delDia(hoy);

        return habitos.stream()
                .map(habito -> construirVista(habito,
                        vigenteDeCatalogo(horarios.getOrDefault(habito.id(), List.of()), diaPrograma, tipoDia),
                        preferencias.get(habito.id()), programados.get(habito.id())))
                .toList();
    }

    private static HorarioDeHabito construirVista(Habito habito, HorarioHabito catalogo,
                                                    PreferenciaHorario preferencia,
                                                    CambioHorarioPendiente programado) {
        LocalTime horaDisparo = primeraNoNula(preferencia != null ? preferencia.horaDisparo() : null,
                catalogo != null ? catalogo.horaDisparo() : null);
        LocalTime horaLimite = primeraNoNula(preferencia != null ? preferencia.horaLimite() : null,
                catalogo != null ? catalogo.horaLimite() : null);
        CambioProgramado cambio = programado == null ? null
                : new CambioProgramado(programado.horaDisparo(), programado.horaLimite(),
                        programado.fechaEfectiva());
        return new HorarioDeHabito(habito.id(), habito.titulo(), horaDisparo, horaLimite, preferencia != null,
                cambio);
    }

    /**
     * Un participante que todavia no activo su programa esta en dia 0, y NINGUN horario del
     * catalogo aplica ahi: todos arrancan en el dia 1. Sin este ajuste, Plan le mostraba los 22
     * habitos sin hora, amontonados en un solo bloque del dia — no podia planificar nada
     * justo cuando mas lo necesita, que es antes de empezar. Se le muestra el horario que va a
     * regir su PRIMER dia; en cuanto el programa arranca, {@code diaPrograma} ya es real y esto
     * no interviene.
     */
    private static HorarioHabito vigenteDeCatalogo(List<HorarioHabito> horarios, int diaPrograma, TipoDia tipoDia) {
        int diaAConsultar = Math.max(diaPrograma, PRIMER_DIA_DEL_PROGRAMA);
        return horarios.stream().filter(h -> h.aplicaEnDia(diaAConsultar, tipoDia)).findFirst().orElse(null);
    }

    /** Preferencia del participante gana si esta seteada; si no, el default del catalogo. */
    private static LocalTime primeraNoNula(LocalTime dePreferencia, LocalTime deCatalogo) {
        return dePreferencia != null ? dePreferencia : deCatalogo;
    }

    /**
     * Misma cuenta que informa el PATCH: en la semana de acomodo no se consulta el historial
     * (no hay cupo que gastar); pasada esa semana, cuenta habitos DISTINTOS tocados desde el
     * inicio de la semana de programa. Un cambio todavia programado no figura aca — recien
     * cobra cupo el dia que pasa a regir (ver {@code PromocionCambioHorarioService}); el
     * cliente lo ve igual en {@code cambioProgramado}.
     */
    private CuotaEdicion cuotaDe(UserId actorId, LocalDate hoy, int diaPrograma) {
        boolean semanaLibre = CuotaEdicionHorario.esSemanaDeAcomodoLibre(diaPrograma);
        int usados = semanaLibre ? 0
                : historialPort.distintosHabitosCambiadosDesde(actorId,
                        CuotaEdicionHorario.inicioSemanaPrograma(hoy, diaPrograma)).size();
        CuotaEdicionHorario cuota = CuotaEdicionHorario.de(usados, semanaLibre);
        return new CuotaEdicion(cuota.usados(), cuota.restantes(), cuota.limite(), cuota.periodo());
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
