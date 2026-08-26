package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase.RocaDiariaVista;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.rocks.domain.model.dashboard.BloqueoPlanificacion;
import com.renaser.os.rocks.domain.model.dashboard.DiaGrillaSemanal;
import com.renaser.os.rocks.domain.model.dashboard.EstadoRitmoRocas;
import com.renaser.os.rocks.domain.model.dashboard.ProgresoSemanal;
import com.renaser.os.rocks.domain.model.rocadiaria.VentanaPlanificacionDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.rocks.domain.model.rocasemanal.VentanaPlanificacionSemanal;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Hueco #15: dashboard agregado de la pantalla principal de Rocas. Compone
 * lecturas ya existentes del propio módulo (masters/semanales/hoy) en vez de
 * duplicar sus reglas — solo agrega presentación (grilla semanal, ritmo,
 * compuertas de planificación), sin inventar ninguna regla de negocio nueva:
 * todo lo de acá está citado contra {@code rocks/service.ts} del repo viejo
 * (ver {@code docs/MODULO_ROCKS.md} §9).
 */
@Service
public class DashboardRocasService implements ConsultarDashboardRocasUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final ConsultarRocasMaestrasUseCase rocasMaestrasUseCase;
    private final ConsultarRocasSemanalesUseCase rocasSemanalesUseCase;
    private final ConsultarRocasDeHoyUseCase rocasDeHoyUseCase;
    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final CargarConteoDiarioRocasPort conteoPort;
    private final Clock clock;

    public DashboardRocasService(ConsultarProgresoParticipanteRocksPort progresoPort,
                                  ConsultarRocasMaestrasUseCase rocasMaestrasUseCase,
                                  ConsultarRocasSemanalesUseCase rocasSemanalesUseCase,
                                  ConsultarRocasDeHoyUseCase rocasDeHoyUseCase, LoadRocaDiariaPort loadRocaDiariaPort,
                                  CargarConteoDiarioRocasPort conteoPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.rocasMaestrasUseCase = rocasMaestrasUseCase;
        this.rocasSemanalesUseCase = rocasSemanalesUseCase;
        this.rocasDeHoyUseCase = rocasDeHoyUseCase;
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.conteoPort = conteoPort;
        this.clock = clock;
    }

    @Override
    public DashboardRocas dashboard(UserId actorId) {
        ProgresoParticipanteRocks progreso = requireProgreso(actorId);
        ZoneId zona = progreso.zona();
        Instant ahora = clock.now();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();

        if (hoy.isBefore(progreso.fechaInicio())) {
            return dashboardProgramaNoIniciado(actorId, progreso);
        }

        ContextoSemana semana = resolverSemana(progreso, hoy);
        List<RocaMaestra> maestras = rocasMaestrasUseCase.misRocasMaestras(actorId);
        boolean rocasDesbloqueadas = maestras.size() >= EjeObjetivo.values().length;

        List<RocaSemanalVista> semanalesVista = cargarRocasSemanalesVista(actorId, semana.numeroSemana(), zona, ahora);
        boolean tieneRocaSemanal = semanalesVista.size() >= EjeObjetivo.values().length;

        List<DiaRocas> conteoSemana = conteoDiario(actorId, semana.inicio(), semana.fin());
        List<DiaGrillaSemanal> grilla = construirGrilla(semana.inicio(), semana.fin(), hoy, conteoSemana);
        int progresoSemanalPct = calcularProgresoSemanal(conteoSemana, hoy);

        int diasCompletados = contarDiasCompletadosUltimos7(actorId, hoy);
        EstadoRitmoRocas ritmo = EstadoRitmoRocas.calcular(diasCompletados);

        boolean planificacionBloqueada = calcularPlanificacionBloqueada(actorId, progreso, ahora, zona, hoy);
        Compuertas compuertas = resolverCompuertas(actorId, progreso, rocasDesbloqueadas, ahora, zona, hoy);
        List<RocaDiariaVista> rocasDeHoy = rocasDeHoyUseCase.hoy(actorId);

        return new DashboardRocas(progreso.diaPrograma(), semana.numeroSemana(), semana.inicio(), semana.fin(),
                maestras, rocasDesbloqueadas, tieneRocaSemanal, semanalesVista, grilla, ritmo, diasCompletados,
                progresoSemanalPct, planificacionBloqueada, compuertas.puedeCrearPlanDiario(),
                compuertas.puedeCrearPlanSemanal(), compuertas.planificacionSemanalTardia(), rocasDeHoy,
                progreso.fechaInicio());
    }

    /**
     * El programa todavía no arrancó para este participante (mismo guard que
     * {@code getDashboard} del repo viejo, {@code rocks/service.ts:841-873}):
     * se responde el mismo contrato con colecciones vacías en vez de un error,
     * para que la pantalla de Rocas no se rompa antes del día 1. Las Rocas
     * Maestras SÍ se devuelven — se definen en el onboarding, antes de empezar.
     */
    private DashboardRocas dashboardProgramaNoIniciado(UserId actorId, ProgresoParticipanteRocks progreso) {
        List<RocaMaestra> maestras = rocasMaestrasUseCase.misRocasMaestras(actorId);
        SemanaPrograma.LimitesSemana limites = SemanaPrograma.limites(progreso.fechaInicio(), 1);
        boolean rocasDesbloqueadas = maestras.size() >= EjeObjetivo.values().length;
        return new DashboardRocas(progreso.diaPrograma(), 1, limites.inicio(), limites.fin(), maestras,
                rocasDesbloqueadas, false, List.of(), List.of(), EstadoRitmoRocas.OK, 0, 0, false, false, false,
                false, List.of(), progreso.fechaInicio());
    }

    /** Semana de programa de {@code hoy}, recortada al fin del programa (día 90). */
    private static ContextoSemana resolverSemana(ProgresoParticipanteRocks progreso, LocalDate hoy) {
        int numeroSemana = SemanaPrograma.numeroSemanaParaFecha(progreso.fechaInicio(), hoy);
        SemanaPrograma.LimitesSemana limites = SemanaPrograma.limites(progreso.fechaInicio(), numeroSemana);
        LocalDate finPrograma = SemanaPrograma.finDelPrograma(progreso.fechaInicio());
        LocalDate fin = limites.fin().isBefore(finPrograma) ? limites.fin() : finPrograma;
        return new ContextoSemana(numeroSemana, limites.inicio(), fin);
    }

    private List<RocaSemanalVista> cargarRocasSemanalesVista(UserId actorId, int numeroSemana, ZoneId zona,
                                                               Instant ahora) {
        return rocasSemanalesUseCase.misRocasSemanales(actorId, numeroSemana).stream()
                .map(r -> new RocaSemanalVista(r, esEditable(r, zona, ahora)))
                .toList();
    }

    private static boolean esEditable(RocaSemanal roca, ZoneId zona, Instant ahora) {
        EstadoPlazo plazoAlCrear = VentanaPlanificacionSemanal.plazoAlCrear(roca.creadoEn(), zona);
        return VentanaPlanificacionSemanal.puedeEditar(plazoAlCrear, roca.creadoEn(), ahora, zona);
    }

    private List<DiaRocas> conteoDiario(UserId actorId, LocalDate desde, LocalDate hasta) {
        return conteoPort.conteoDiarioPorParticipante(List.of(actorId), desde, hasta).getOrDefault(actorId, List.of());
    }

    private static List<DiaGrillaSemanal> construirGrilla(LocalDate inicio, LocalDate fin, LocalDate hoy,
                                                            List<DiaRocas> conteo) {
        // Rango invertido: pasa cuando hoy es posterior al fin del programa, porque resolverSemana
        // recorta `fin` contra finDelPrograma pero `inicio` es la semana en curso. Sin este guard el
        // bucle simplemente no entra y devuelve una grilla vacia por accidente. Se devuelve vacia
        // igual, pero a proposito y en un solo lugar: que ve un graduado en esta pantalla es una
        // pregunta de producto abierta (isProgramCompleted, docs/MODULO_ROCKS.md), y no se inventa acá.
        if (inicio.isAfter(fin)) {
            return List.of();
        }
        Map<LocalDate, DiaRocas> porFecha = conteo.stream().collect(Collectors.toMap(DiaRocas::fecha, d -> d));
        List<DiaGrillaSemanal> dias = new ArrayList<>();
        for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            dias.add(construirDia(fecha, hoy, porFecha.get(fecha)));
        }
        return dias;
    }

    /** {@code completadas}/{@code total} en {@code null} para un día futuro — ver {@link DiaGrillaSemanal}. */
    private static DiaGrillaSemanal construirDia(LocalDate fecha, LocalDate hoy, DiaRocas dia) {
        boolean futuro = fecha.isAfter(hoy);
        Integer completadas = futuro ? null : (dia == null ? 0 : dia.completadas());
        Integer total = futuro ? null : (dia == null ? null : dia.total());
        return new DiaGrillaSemanal(fecha, fecha.getDayOfWeek(), completadas, total, fecha.equals(hoy));
    }

    private static int calcularProgresoSemanal(List<DiaRocas> conteo, LocalDate hoy) {
        List<DiaRocas> transcurridos = conteo.stream().filter(d -> !d.fecha().isAfter(hoy)).toList();
        int totalPlanificado = transcurridos.stream().mapToInt(DiaRocas::total).sum();
        int totalCompletado = transcurridos.stream().mapToInt(DiaRocas::completadas).sum();
        return ProgresoSemanal.calcular(totalPlanificado, totalCompletado);
    }

    /** Días con al menos una Roca completada de los últimos 7, terminando AYER (no incluye hoy). */
    private int contarDiasCompletadosUltimos7(UserId actorId, LocalDate hoy) {
        List<DiaRocas> ultimos7 = conteoDiario(actorId, hoy.minusDays(7), hoy.minusDays(1));
        return (int) ultimos7.stream().filter(d -> d.completadas() > 0).count();
    }

    /**
     * Ley II — corta ANTES de consultar la BD cuando el día/hora todavía no
     * calificaba (mismo criterio que {@code computePlanningBlocked} del repo
     * viejo: "only queries the DB when the day/hour conditions are already met").
     */
    private boolean calcularPlanificacionBloqueada(UserId actorId, ProgresoParticipanteRocks progreso, Instant ahora,
                                                     ZoneId zona, LocalDate hoy) {
        int horaLocal = ahora.atZone(zona).getHour();
        if (progreso.diaPrograma() < BloqueoPlanificacion.DIA_INICIO_FASE_ROCAS
                || horaLocal < BloqueoPlanificacion.HORA_BLOQUEO) {
            return false;
        }
        int rocasManana = loadRocaDiariaPort.contarDeParticipanteYFecha(actorId, hoy.plusDays(1));
        return BloqueoPlanificacion.bloqueada(progreso.diaPrograma(), horaLocal, rocasManana);
    }

    private Compuertas resolverCompuertas(UserId actorId, ProgresoParticipanteRocks progreso,
                                           boolean rocasDesbloqueadas, Instant ahora, ZoneId zona, LocalDate hoy) {
        boolean ventanaDiariaAbierta = VentanaPlanificacionDiaria.abierta(ahora, zona);
        LocalDate manana = hoy.plusDays(1);
        int semanaManana = SemanaPrograma.numeroSemanaParaFecha(progreso.fechaInicio(), manana);
        boolean tieneSemanalParaManana = !rocasSemanalesUseCase.misRocasSemanales(actorId, semanaManana).isEmpty();
        boolean puedeCrearPlanDiario = rocasDesbloqueadas && ventanaDiariaAbierta && tieneSemanalParaManana;
        boolean planificacionSemanalTardia = !VentanaPlanificacionSemanal.abierta(ahora, zona);
        return new Compuertas(puedeCrearPlanDiario, rocasDesbloqueadas, planificacionSemanalTardia);
    }

    /** SUSPENDIDO -> 403. Rol distinto de TRAINEE -> 403 (solo el aprendiz opera sus rocas). */
    private ProgresoParticipanteRocks requireProgreso(UserId actorId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }

    private record ContextoSemana(int numeroSemana, LocalDate inicio, LocalDate fin) {
    }

    private record Compuertas(boolean puedeCrearPlanDiario, boolean puedeCrearPlanSemanal,
                               boolean planificacionSemanalTardia) {
    }
}
