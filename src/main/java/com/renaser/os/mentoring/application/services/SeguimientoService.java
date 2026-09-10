package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAprendiz;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase;
import com.renaser.os.points.api.CalculoCumplimientoPort;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Seguimiento semanal de un alumno y evaluación mensual propia del mentor.
 *
 * <p>Las dos lecturas comparten la misma disciplina: la semana sale de las obligaciones
 * históricas —un snapshot por día, no una proyección del catálogo de hoy—, así que reprogramar
 * un hábito no reescribe la semana pasada; y la nota sale del motor único de {@code points},
 * que el frontend no recalcula.
 *
 * <p>Todo entra por APIs públicas: este módulo no conoce el agregado de asignaciones ni el de
 * evidencias.
 */
@Service
public class SeguimientoService implements ConsultarSeguimientoSemanalUseCase, ConsultarEvaluacionPropiaUseCase {

    private static final String COBERTURA_COMPLETA = "COMPLETA";
    private static final String COBERTURA_SIN_DATOS = "SIN_DATOS";

    private static final String ENTREGA_NO_REQUERIDA = "NO_REQUERIDA";
    private static final String ENTREGA_SIN_ENTREGA = "SIN_ENTREGA";
    private static final String ENTREGA_ENTREGADA = "ENTREGADA";

    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final AcompanamientoFinder acompanamientoFinder;
    private final ObligacionesHistoricasFinder obligacionesFinder;
    private final EntregasPorRegistroFinder entregasFinder;
    private final CalculoCumplimientoPort calculoCumplimiento;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public SeguimientoService(AcompanamientoFinder acompanamientoFinder,
                               ObligacionesHistoricasFinder obligacionesFinder,
                               EntregasPorRegistroFinder entregasFinder,
                               CalculoCumplimientoPort calculoCumplimiento,
                               ParticipacionProgramaFinder participacionProgramaFinder,
                               UserSummaryFinder userSummaryFinder, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.obligacionesFinder = obligacionesFinder;
        this.entregasFinder = entregasFinder;
        this.calculoCumplimiento = calculoCumplimiento;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    // ── Seguimiento semanal ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SemanaDelAlumno semanaDe(ConsultaSemana consulta) {
        Instant ahora = clock.now();
        UserId alumnoId = UserId.of(consulta.alumnoId());

        if (!acompanamientoFinder.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        // Y ademas que el alumno pedido sea de ESE grupo: sin esto, un mentor legitimo podria
        // pedir el detalle de cualquiera pasando el id de su propio grupo (V12).
        if (!acompanamientoFinder.aprendicesVigentes(consulta.grupoId(), ahora).contains(alumnoId)) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompanas");
        }

        GrupoBasico grupo = acompanamientoFinder.grupo(consulta.grupoId())
                .orElseThrow(() -> new NoSuchElementException("Grupo no encontrado"));
        ParticipacionPrograma participacion = participacionProgramaFinder.deParticipante(alumnoId)
                .orElseThrow(() -> new NoSuchElementException("Alumno no encontrado"));

        // La semana se ancla en la zona DEL ALUMNO, no en la del servidor ni en la del mentor:
        // sus dias son los que se estan mirando.
        ZoneId zona = participacion.zona();
        LocalDate lunes = (consulta.inicioDeSemana() != null
                ? consulta.inicioDeSemana()
                : ahora.atZone(zona).toLocalDate()).with(DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);

        List<ObligacionHabito> obligaciones =
                obligacionesFinder.porParticipantesEntre(List.of(alumnoId), lunes, domingo);
        Map<UUID, EntregaDeEvidencia> entregas =
                entregasFinder.porRegistros(obligaciones.stream().map(ObligacionHabito::registroId).toList());

        String nombre = userSummaryFinder.findById(alumnoId).map(UserSummary::fullName).orElse(null);
        // Sin una sola obligacion en toda la semana no se afirma "no cumplio": puede que el
        // padron de ese periodo no se haya generado. Es SIN_DATOS, que es otra cosa.
        String cobertura = obligaciones.isEmpty() ? COBERTURA_SIN_DATOS : COBERTURA_COMPLETA;

        return new SemanaDelAlumno(grupo.grupoId(), alumnoId.value(), nombre, lunes, domingo,
                participacion.inscrito() ? participacion.diaPrograma() : null, zona.getId(),
                armarDias(lunes, domingo, obligaciones, entregas), resumir(obligaciones, entregas),
                cobertura, ahora);
    }

    private List<DiaDelAlumno> armarDias(LocalDate lunes, LocalDate domingo, List<ObligacionHabito> obligaciones,
                                          Map<UUID, EntregaDeEvidencia> entregas) {
        Map<LocalDate, List<ObligacionDelDia>> porFecha = new LinkedHashMap<>();
        Map<LocalDate, Integer> diaDePrograma = new LinkedHashMap<>();
        for (LocalDate fecha = lunes; !fecha.isAfter(domingo); fecha = fecha.plusDays(1)) {
            // Los siete dias siempre estan, aunque vengan vacios: un dia sin obligaciones es un
            // dato ("no habia nada programado"), no un hueco que la pantalla deba adivinar.
            porFecha.put(fecha, new ArrayList<>());
        }
        for (ObligacionHabito obligacion : obligaciones) {
            porFecha.computeIfAbsent(obligacion.fecha(), f -> new ArrayList<>())
                    .add(aObligacionDelDia(obligacion, entregas.get(obligacion.registroId())));
            diaDePrograma.putIfAbsent(obligacion.fecha(), obligacion.diaPrograma());
        }
        return porFecha.entrySet().stream()
                .map(e -> new DiaDelAlumno(e.getKey(), diaDePrograma.get(e.getKey()), e.getValue()))
                .toList();
    }

    private static ObligacionDelDia aObligacionDelDia(ObligacionHabito obligacion, EntregaDeEvidencia entrega) {
        String estadoEntrega;
        if (!obligacion.requiereEvidencia()) {
            // Hay habitos que se acreditan de otro modo. Pedirles archivo seria inventar una
            // obligacion que nunca existio (plan.md §7).
            estadoEntrega = ENTREGA_NO_REQUERIDA;
        } else {
            estadoEntrega = entrega == null ? ENTREGA_SIN_ENTREGA : ENTREGA_ENTREGADA;
        }
        return new ObligacionDelDia(obligacion.registroId(), obligacion.titulo(), obligacion.estado().name(),
                obligacion.requiereEvidencia(), estadoEntrega,
                entrega == null ? null : entrega.primeraEntregaEn(),
                entrega == null ? null : entrega.estadoRevision().name(),
                entrega == null ? null : entrega.evidenciaId());
    }

    private static ResumenSemana resumir(List<ObligacionHabito> obligaciones,
                                          Map<UUID, EntregaDeEvidencia> entregas) {
        int cumplidas = (int) obligaciones.stream().filter(o -> o.estado().cumplido()).count();
        int sinCumplir = (int) obligaciones.stream().filter(o -> o.estado().vencidoSinCumplir()).count();
        int conEntrega = (int) obligaciones.stream().filter(o -> entregas.containsKey(o.registroId())).count();
        return new ResumenSemana(obligaciones.size(), cumplidas, conEntrega,
                obligaciones.size() - cumplidas - sinCumplir, sinCumplir);
    }

    // ── Evaluación mensual propia ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public EvaluacionPropia evaluacionDe(UserId actorId, YearMonth mes) {
        ZoneId zona = zonaDe(actorId);
        Instant inicioDelMes = mes.atDay(1).atStartOfDay(zona).toInstant();
        /*
         * El corte nunca va hacia adelante. Una obligacion "vencio" cuando termino su dia local;
         * las de hoy todavia estan corriendo y las de mañana ni existen. Sin este tope, evaluar
         * a mitad de mes contaba todo lo que falta como incumplido y le bajaba la nota al mentor
         * por dias que la persona todavia tiene por delante (plan.md §8.2: "excluir futuras").
         */
        Instant finDelMes = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
        Instant corte = clock.now().isBefore(finDelMes) ? clock.now() : finDelMes;

        if (!corte.isAfter(inicioDelMes)) {
            // El mes pedido todavia no empezo.
            return sinDatos(mes, zona);
        }
        List<TramoDeAcompanamiento> tramos =
                acompanamientoFinder.tramosDeMentor(actorId, inicioDelMes, corte);

        // Ventana por alumno = interseccion de las dos pertenencias: la del mentor al grupo y la
        // del alumno al grupo. Es lo que impide atribuirle obligaciones de alguien que entro
        // despues de que el se fue (plan.md §8.1).
        Map<UUID, List<VentanaEvaluacion>> ventanasPorAlumno = new LinkedHashMap<>();
        for (TramoDeAcompanamiento tramo : tramos) {
            Instant hasta = tramo.hasta() == null ? corte : tramo.hasta();
            for (TramoDeAprendiz deAlumno :
                    acompanamientoFinder.tramosDeAprendices(tramo.grupoId(), tramo.desde(), hasta)) {
                ventanasPorAlumno.computeIfAbsent(deAlumno.aprendizId().value(), a -> new ArrayList<>())
                        .add(new VentanaEvaluacion(deAlumno.desde(),
                                deAlumno.hasta() == null ? hasta : deAlumno.hasta()));
            }
        }

        EvaluacionCumplimiento evaluacion = ventanasPorAlumno.isEmpty()
                ? calculoCumplimiento.evaluar(List.of(), Map.of())
                : calcular(ventanasPorAlumno, zona);

        return new EvaluacionPropia(mes.toString(), zona.getId(), evaluacion.porcentaje(),
                evaluacion.entregadas(), evaluacion.esperadas(), evaluacion.aprendicesEvaluados(),
                evaluacion.aprendicesExcluidos(), evaluacion.tardiasFueraDeVentana(),
                evaluacion.verificadas(), evaluacion.estado().name(), evaluacion.versionFormula(),
                clock.now(), tramosVisibles(tramos, zona));
    }

    private EvaluacionPropia sinDatos(YearMonth mes, ZoneId zona) {
        EvaluacionCumplimiento vacia = calculoCumplimiento.evaluar(List.of(), Map.of());
        return new EvaluacionPropia(mes.toString(), zona.getId(), null, 0, 0, 0, 0, 0, 0,
                vacia.estado().name(), vacia.versionFormula(), clock.now(), List.of());
    }

    private EvaluacionCumplimiento calcular(Map<UUID, List<VentanaEvaluacion>> ventanasPorAlumno, ZoneId zona) {
        LocalDate desde = ventanasPorAlumno.values().stream().flatMap(List::stream)
                .map(v -> v.desde().atZone(zona).toLocalDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate hasta = ventanasPorAlumno.values().stream().flatMap(List::stream)
                .map(v -> (v.hasta() == null ? clock.now() : v.hasta()).atZone(zona).toLocalDate())
                .max(Comparator.naturalOrder()).orElseThrow();

        List<ObligacionHabito> obligaciones = obligacionesFinder.porParticipantesEntre(
                ventanasPorAlumno.keySet().stream().map(UserId::of).toList(), desde, hasta);
        Map<UUID, EntregaDeEvidencia> entregas =
                entregasFinder.porRegistros(obligaciones.stream().map(ObligacionHabito::registroId).toList());

        return calculoCumplimiento.evaluar(obligaciones.stream()
                .filter(ObligacionHabito::exigible)
                .map(o -> aObligacionDeEvidencia(o, entregas.get(o.registroId()), zona))
                .toList(), ventanasPorAlumno);
    }

    /**
     * El vencimiento se toma al cierre del día local de la obligación. Es lo que decide a qué
     * tramo pertenece cuando el mentor cambió a mitad de mes.
     */
    private static ObligacionEvidencia aObligacionDeEvidencia(ObligacionHabito obligacion,
                                                               EntregaDeEvidencia entrega, ZoneId zona) {
        Instant venceEn = obligacion.fecha().plusDays(1).atStartOfDay(zona).toInstant().minusMillis(1);
        return new ObligacionEvidencia(obligacion.registroId(), obligacion.participanteId().value(), venceEn,
                entrega == null ? null : entrega.primeraEntregaEn(),
                entrega != null && entrega.verificada());
    }

    private List<TramoEvaluado> tramosVisibles(List<TramoDeAcompanamiento> tramos, ZoneId zona) {
        return tramos.stream()
                .map(t -> new TramoEvaluado(t.grupoNombre(), t.desde().atZone(zona).toLocalDate(),
                        t.hasta() == null ? null : t.hasta().atZone(zona).toLocalDate()))
                .toList();
    }

    /** Zona de la cohorte del grupo que acompaña; Lima como último recurso. */
    private ZoneId zonaDe(UserId actorId) {
        return acompanamientoFinder.tramosDeMentor(actorId, Instant.EPOCH, null).stream()
                .findFirst()
                .flatMap(t -> acompanamientoFinder.grupo(t.grupoId()))
                .map(g -> ZoneId.of(g.zonaHoraria()))
                .orElse(ZONA_POR_DEFECTO);
    }
}
