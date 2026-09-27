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
import java.util.Optional;
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

        Optional<SemanaPrograma> delParticipante = progreso.semanas(hoy);
        if (delParticipante.isEmpty() || hoy.isBefore(delParticipante.get().primerDia())) {
            return dashboardProgramaNoIniciado(actorId, progreso, hoy);
        }
        SemanaPrograma semanas = delParticipante.get();

        ContextoSemana semana = resolverSemana(semanas, hoy);
        List<RocaMaestra> maestras = rocasMaestrasUseCase.misRocasMaestras(actorId);
        boolean rocasDesbloqueadas = maestras.size() >= EjeObjetivo.values().length;

        List<RocaSemanalVista> semanalesVista = cargarRocasSemanalesVista(actorId, semana.numeroSemana(), zona, ahora);
        /* Con UNO alcanza (2026-09-22). Decia `>= EjeObjetivo.values().length`, o sea los tres, y
           desde que el plan semanal se puede guardar con un solo eje eso dejaba a la persona
           planificando su eje principal y leyendo igual "todavia no armaste esta semana". El
           dashboard pregunta si la semana esta ARMADA, no si esta completa. */
        boolean tieneRocaSemanal = !semanalesVista.isEmpty();

        List<DiaRocas> conteoSemana = conteoDiario(actorId, semana.inicio(), semana.fin());
        List<DiaGrillaSemanal> grilla = construirGrilla(semana.inicio(), semana.fin(), hoy, conteoSemana);
        int progresoSemanalPct = calcularProgresoSemanal(conteoSemana, hoy);
        int diasCompletados = contarDiasCompletadosUltimos7(actorId, hoy);

        boolean planificacionBloqueada = calcularPlanificacionBloqueada(actorId, progreso, ahora, zona, hoy);
        Compuertas compuertas = resolverCompuertas(actorId, semanas, rocasDesbloqueadas, ahora, zona);
        List<RocaDiariaVista> rocasDeHoy = rocasDeHoyUseCase.hoy(actorId);

        return new DashboardRocas(progreso.diaPrograma(), semana.numeroSemana(), semana.inicio(), semana.fin(),
                maestras, rocasDesbloqueadas, tieneRocaSemanal, semanalesVista, grilla,
                EstadoRitmoRocas.calcular(diasCompletados), diasCompletados, progresoSemanalPct,
                planificacionBloqueada, compuertas.puedeCrearPlanDiario(), compuertas.puedeCrearPlanSemanal(),
                compuertas.planificacionSemanalTardia(), rocasDeHoy, progreso.diaUnoElegido());
    }

    /**
     * El programa todavía no arrancó para este participante (mismo guard que
     * {@code getDashboard} del repo viejo, {@code rocks/service.ts:841-873}):
     * se responde el mismo contrato con colecciones vacías en vez de un error,
     * para que la pantalla de Rocas no se rompa antes del día 1. Las Rocas
     * Maestras SÍ se devuelven — se definen en el onboarding, antes de empezar.
     *
     * <p><b>Sin Día 1 elegido (D-203, D-201) la semana 1 va sin fechas</b>: {@code inicioSemana},
     * {@code finSemana} y {@code fechaInicioPrograma} en {@code null}. Antes salían de la fecha provisional
     * del alta, que no es un Día 1, y el acompañante las repetía como si lo fueran. Con el Día 1 elegido y
     * todavía por llegar, son las de la semana 1 de verdad.
     */
    private DashboardRocas dashboardProgramaNoIniciado(UserId actorId, ProgresoParticipanteRocks progreso,
                                                       LocalDate hoy) {
        List<RocaMaestra> maestras = rocasMaestrasUseCase.misRocasMaestras(actorId);
        Optional<SemanaPrograma.LimitesSemana> limites = progreso.semanas(hoy).map(s -> s.limites(1));
        boolean rocasDesbloqueadas = maestras.size() >= EjeObjetivo.values().length;
        return new DashboardRocas(progreso.diaPrograma(), 1,
                limites.map(SemanaPrograma.LimitesSemana::inicio).orElse(null),
                limites.map(SemanaPrograma.LimitesSemana::fin).orElse(null), maestras, rocasDesbloqueadas, false,
                List.of(), List.of(), EstadoRitmoRocas.OK, 0, 0, false, false, false, false, List.of(),
                progreso.diaUnoElegido());
    }

    /**
     * Semana de programa de {@code hoy}: lunes a domingo, contada desde la semana del primer día efectivo
     * (D-203). {@link SemanaPrograma#limites} ya hace terminar la 13 el día 90.
     *
     * <p><b>Corregido 2026-09-27 (D-203).</b> Decía «un bloque de siete días del programa, con el ajuste
     * (D-192)». La semana volvió a ser de calendario; lo del ajuste se mantiene.
     */
    private static ContextoSemana resolverSemana(SemanaPrograma semanas, LocalDate hoy) {
        int numeroSemana = semanas.numeroSemanaParaFecha(hoy);
        SemanaPrograma.LimitesSemana limites = semanas.limites(numeroSemana);
        return new ContextoSemana(numeroSemana, limites.inicio(), limites.fin());
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
        // Rango invertido: hasta D-192 pasaba cuando hoy era posterior al fin del programa (la semana de
        // hoy era la 14 o más y `fin` se recortaba al día 90). Desde D-203 la semana de hoy se acota a la
        // 13 y la 13 siempre termina el día 90, después de su lunes: un graduado ve su última semana. Se
        // deja la guarda para que un recorte hecho por fuera no vuelva a dar una grilla vacía por
        // accidente. Qué más debería ver un graduado en esta pantalla sigue siendo una pregunta de
        // producto abierta (isProgramCompleted, docs/MODULO_ROCKS.md), y no se inventa acá.
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

    private Compuertas resolverCompuertas(UserId actorId, SemanaPrograma semanas, boolean rocasDesbloqueadas,
                                           Instant ahora, ZoneId zona) {
        boolean ventanaDiariaAbierta = VentanaPlanificacionDiaria.abierta(ahora, zona);
        LocalDate manana = ahora.atZone(zona).toLocalDate().plusDays(1);
        int semanaManana = semanas.numeroSemanaParaFecha(manana);
        /* Pasado el dia 90 la semana se acota a la 13 (D-192, D-203) y "manana" tendria objetivo semanal,
           pero ya no es un dia del programa: FechasPlanificables lo rechazaria al guardar. */
        boolean tieneSemanalParaManana = !manana.isAfter(semanas.finDelPrograma())
                && !rocasSemanalesUseCase.misRocasSemanales(actorId, semanaManana).isEmpty();
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
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
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
