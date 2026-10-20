package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import com.renaser.os.habits.domain.model.horario.HorariosDelHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.CuotaEdicionHorario;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final HorariosDelHabito SIN_HORARIOS = HorariosDelHabito.de(List.of());

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadCambioHorarioPendientePort loadCambioPendientePort;
    private final HistorialCambioHorarioPort historialPort;
    /** D-200: si un habito que quedo por debajo del inicio de su horario ya corrio. */
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final Clock clock;
    /** Spec 001 (D-264): el mismo filtro por rol que la generacion. */
    private final CatalogoAplicable catalogoAplicable;

    public ConsultaPreferenciasHorarioService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                               LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                               LoadPreferenciaHorarioPort loadPreferenciaPort,
                                               LoadCambioHorarioPendientePort loadCambioPendientePort,
                                               HistorialCambioHorarioPort historialPort,
                                               LoadRegistroHabitoPort loadRegistroPort, Clock clock,
                                               CatalogoAplicable catalogoAplicable) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadCambioPendientePort = loadCambioPendientePort;
        this.historialPort = historialPort;
        this.loadRegistroPort = loadRegistroPort;
        this.clock = clock;
        this.catalogoAplicable = catalogoAplicable;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPreferenciasHorario consultar(UserId actorId) {
        return consultar(actorId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPreferenciasHorario consultar(UserId actorId, LocalDate fecha) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate();
        LocalDate objetivo = fecha == null ? hoy : fecha;
        int diaObjetivo = Math.toIntExact(progreso.diaPrograma() + ChronoUnit.DAYS.between(hoy, objetivo));
        List<Habito> habitos = habitosActivosDe(actorId, progreso, objetivo);
        return new ResumenPreferenciasHorario(horariosDe(actorId, habitos, objetivo, diaObjetivo),
                cuotaDe(actorId, objetivo, diaObjetivo));
    }

    /** Mismo criterio que {@code RegistroService.generar}: catalogo aplicable ESE dia + personales del aprendiz. */
    private List<Habito> habitosActivosDe(UserId actorId, ProgresoParticipanteHabits progreso, LocalDate dia) {
        List<Habito> habitos = new ArrayList<>(catalogoAplicable.globalesPara(actorId, progreso, dia));
        habitos.addAll(loadHabitoPort.personalesActivosDe(actorId));
        return habitos;
    }

    private List<HorarioDeHabito> horariosDe(UserId actorId, List<Habito> habitos, LocalDate hoy, int diaPrograma) {
        if (habitos.isEmpty()) {
            return List.of();
        }
        Set<HabitoId> ids = habitos.stream().map(Habito::id).collect(Collectors.toSet());
        // D-216 (TZ-15): con el habito al lado, porque el primer dia de uno PERSONAL ya esta alcanzado.
        Map<HabitoId, HorariosDelHabito> horarios = HorariosDelHabito.porHabito(habitos, loadHorarioPort.porHabitos(ids));
        // Spec 008: el aviso del listado es el GENERAL del habito (sin el del dia de la semana consultado),
        // porque el APK anterior lo reescribe como general y el editor nuevo lo toma como lo que heredan los
        // dias sin aviso propio. El de cada dia va aparte, en `diasDeLaSemana`.
        Map<HabitoId, PreferenciaHorario> preferencias = loadPreferenciaPort
                .porParticipanteHabitosYFechaSinAvisoDelDia(actorId, ids, hoy)
                .stream().collect(Collectors.toMap(PreferenciaHorario::habitoId, p -> p));
        List<CambioHorarioPendiente> cambios = loadCambioPendientePort.deParticipante(actorId);
        Map<HabitoId, CambioHorarioPendiente> programados = cambios.stream()
                .filter(c -> c.fechaEfectiva().isAfter(hoy))
                .collect(Collectors.toMap(CambioHorarioPendiente::habitoId, c -> c));
        TipoDia tipoDia = TipoDia.delDia(hoy);
        Map<HabitoId, Integer> yaGenerados = diasMasAltosYaGenerados(actorId, diaPrograma, tipoDia, horarios);
        BaseDeLaSemana base = baseDeLaSemana(actorId, ids, cambios, hoy);

        return habitos.stream()
                .map(habito -> {
                    HorarioHabito catalogo = vigenteDeCatalogo(horarios.getOrDefault(habito.id(), SIN_HORARIOS),
                            diaPrograma, tipoDia, yaGenerados.get(habito.id()));
                    return construirVista(habito, catalogo, preferencias.get(habito.id()),
                            programados.get(habito.id()), base.semanaDe(habito.id(), catalogo),
                            base.alarmaGeneral(habito.id()));
                })
                .toList();
    }

    /**
     * Lo que rige POR DEBAJO de los dias de la semana, para resolver los siete dias de cada habito sin una
     * consulta por habito: la preferencia general, el cambio general ya vigente (D-91) y las filas de dia de
     * la semana, cada una pedida UNA vez por el conjunto completo.
     */
    private BaseDeLaSemana baseDeLaSemana(UserId actorId, Set<HabitoId> ids, List<CambioHorarioPendiente> cambios,
                                          LocalDate consultada) {
        Map<HabitoId, PreferenciaHorario> generales = loadPreferenciaPort.porParticipanteYHabitos(actorId, ids)
                .stream().collect(Collectors.toMap(PreferenciaHorario::habitoId, p -> p));
        Map<HabitoId, CambioHorarioPendiente> vigentes = cambios.stream()
                .filter(c -> !c.fechaEfectiva().isAfter(consultada))
                .collect(Collectors.toMap(CambioHorarioPendiente::habitoId, c -> c));
        Map<HabitoId, List<HorarioSemanal>> filas = loadPreferenciaPort.horariosSemanalesDe(actorId, ids).stream()
                .collect(Collectors.groupingBy(f -> f.preferencia().habitoId()));
        return new BaseDeLaSemana(generales, vigentes, filas);
    }

    /** Los tres mapas de {@link #baseDeLaSemana}, y como se mezclan en la semana de UN habito. */
    private record BaseDeLaSemana(Map<HabitoId, PreferenciaHorario> generales,
                                  Map<HabitoId, CambioHorarioPendiente> vigentes,
                                  Map<HabitoId, List<HorarioSemanal>> filas) {

        /** Misma precedencia que `GET /weekdays`: cambio vigente > preferencia general > catalogo. */
        List<SemanaDelHabito.Dia> semanaDe(HabitoId habitoId, HorarioHabito catalogo) {
            PreferenciaHorario general = generales.get(habitoId);
            CambioHorarioPendiente cambio = vigentes.get(habitoId);
            LocalTime disparo = primera(cambio != null ? cambio.horaDisparo() : null,
                    general != null ? general.horaDisparo() : null, catalogo != null ? catalogo.horaDisparo() : null);
            LocalTime limite = primera(cambio != null ? cambio.horaLimite() : null,
                    general != null ? general.horaLimite() : null, catalogo != null ? catalogo.horaLimite() : null);
            return SemanaDelHabito.resolver(filas.getOrDefault(habitoId, List.of()), disparo, limite,
                    RecordatorioDelDia.delGeneral(general), alarmaGeneral(habitoId));
        }

        /**
         * Como suena la alarma del habito, resuelta (spec 009): la de la preferencia GENERAL o, si nadie eligio
         * nada, la de hoy. Sale de la fila general y no de la resuelta por fecha, que puede venir de otra capa.
         */
        CombinacionDeAlarma alarmaGeneral(HabitoId habitoId) {
            PreferenciaHorario general = generales.get(habitoId);
            return CombinacionDeAlarma.COMO_HOY.con(general != null ? general.alarma() : null);
        }

        private static LocalTime primera(LocalTime delCambio, LocalTime delGeneral, LocalTime delCatalogo) {
            return delCambio != null ? delCambio : delGeneral != null ? delGeneral : delCatalogo;
        }
    }

    /**
     * D-200: el mismo "¿ya corrio?" con el que {@code RegistroService} decide generar un habito que
     * quedo por debajo del inicio de su horario tras un retroceso. Sin esto, ese habito se generaba
     * con su hora pero aca (Plan y el acompanante) aparecia sin hora. UNA consulta, y solo si algun
     * habito la necesita.
     */
    private Map<HabitoId, Integer> diasMasAltosYaGenerados(UserId actorId, int diaPrograma, TipoDia tipoDia,
                                                           Map<HabitoId, HorariosDelHabito> horarios) {
        List<HabitoId> candidatos = horarios.entrySet().stream()
                .filter(e -> e.getValue().necesitaSaberSiYaCorrio(diaPrograma, tipoDia))
                .map(Map.Entry::getKey)
                .toList();
        return candidatos.isEmpty() ? Map.of()
                : loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(actorId, candidatos);
    }

    private static HorarioDeHabito construirVista(Habito habito, HorarioHabito catalogo,
                                                    PreferenciaHorario preferencia,
                                                    CambioHorarioPendiente programado,
                                                    List<SemanaDelHabito.Dia> diasDeLaSemana,
                                                    CombinacionDeAlarma alarma) {
        LocalTime horaDisparo = primeraNoNula(preferencia != null ? preferencia.horaDisparo() : null,
                catalogo != null ? catalogo.horaDisparo() : null);
        LocalTime horaLimite = primeraNoNula(preferencia != null ? preferencia.horaLimite() : null,
                catalogo != null ? catalogo.horaLimite() : null);
        CambioProgramado cambio = programado == null ? null
                : new CambioProgramado(programado.horaDisparo(), programado.horaLimite(),
                        programado.fechaEfectiva());
        // Una fila que existe solo para guardar como suena la alarma (spec 009) no eligio ningun horario.
        boolean eligioHorario = preferencia != null && !preferencia.esSoloDeAlarma();
        return new HorarioDeHabito(habito.id(), habito.titulo(), horaDisparo,
                VentanaDelDia.limiteQueSigueAlDisparo(horaDisparo, horaLimite), eligioHorario,
                preferencia != null && preferencia.recordatorioActivo(),
                preferencia != null ? preferencia.minutosRecordatorio() : null,
                cambio, preferencia != null ? preferencia.antelacionesRecordatorio() : null, diasDeLaSemana, alarma);
    }

    /**
     * Un participante que todavia no activo su programa esta en dia 0, y NINGUN horario del
     * catalogo aplica ahi: todos arrancan en el dia 1. Sin este ajuste, Plan le mostraba los 22
     * habitos sin hora, amontonados en un solo bloque del dia — no podia planificar nada
     * justo cuando mas lo necesita, que es antes de empezar. Se le muestra el horario que va a
     * regir su PRIMER dia; en cuanto el programa arranca, {@code diaPrograma} ya es real y esto
     * no interviene.
     *
     * <p>D-200: si el habito ya corrio y un retroceso dejo el dia por debajo del inicio de su
     * horario, rige ese horario (el mismo con el que se lo genera).
     */
    private static HorarioHabito vigenteDeCatalogo(HorariosDelHabito horarios, int diaPrograma, TipoDia tipoDia,
                                                   Integer diaMasAltoYaGenerado) {
        int diaAConsultar = Math.max(diaPrograma, PRIMER_DIA_DEL_PROGRAMA);
        int dia = horarios.diaEfectivo(diaAConsultar, tipoDia, diaMasAltoYaGenerado);
        return horarios.vigentesEn(dia, tipoDia).stream().findFirst().orElse(null);
    }

    /** Preferencia del participante gana si esta seteada; si no, el default del catalogo. */
    private static LocalTime primeraNoNula(LocalTime dePreferencia, LocalTime deCatalogo) {
        return dePreferencia != null ? dePreferencia : deCatalogo;
    }

    /** Cuenta habitos distintos ya cambiados o reservados en la semana de la fecha consultada. */
    private CuotaEdicion cuotaDe(UserId actorId, LocalDate hoy, int diaPrograma) {
        boolean semanaLibre = CuotaEdicionHorario.esSemanaDeAcomodoLibre(diaPrograma);
        LocalDate inicio = CuotaEdicionHorario.inicioSemanaPrograma(hoy, diaPrograma);
        Set<HabitoId> comprometidos = new HashSet<>();
        if (!semanaLibre) {
            comprometidos.addAll(historialPort.distintosHabitosCambiadosDesde(actorId, inicio));
            comprometidos.addAll(loadPreferenciaPort.habitosConHorarioEntre(actorId, inicio, inicio.plusDays(6)));
            loadCambioPendientePort.deParticipante(actorId).stream()
                    .filter(p -> !p.fechaEfectiva().isBefore(inicio) && !p.fechaEfectiva().isAfter(inicio.plusDays(6)))
                    .map(CambioHorarioPendiente::habitoId).forEach(comprometidos::add);
        }
        int usados = comprometidos.size();
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
