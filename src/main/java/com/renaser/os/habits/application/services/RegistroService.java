package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase.RegistrosDelDia;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioResuelto;
import com.renaser.os.habits.domain.model.horario.HorariosDelHabito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.RegistroPoliticasHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.FaseOtorgamiento;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servicio del agregado `registro/` — el corazon del modulo. Integra
 * sincronicamente con `points.api.AjustarPuntosPort` DENTRO de la misma
 * transaccion que completa el registro (CLAUDE.MD §9.1: la misma garantia
 * atomica que ya usa `points`, no un evento).
 */
@Service
public class RegistroService implements ConsultarTracksDelDiaUseCase, GenerarTracksDelDiaUseCase,
        CompletarRegistroUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);
    /** Un habito sin fila en `horarios_habito` no genera track: ningun horario lo cubre. */
    private static final HorariosDelHabito SIN_HORARIOS = HorariosDelHabito.de(List.of());

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    /**
     * Para POST DIARIO EN COMUNIDAD: el unico modo de saber si el aprendiz publico DE VERDAD.
     * Se consume por el `api` de `community`, igual que {@link AjustarPuntosPort} de `points`
     * — nunca leyendo `publicaciones_muro` desde aca (D-41). Solo lo mira
     * {@link PoliticaPostDiarioComunidad}, y solo si a ese habito le toca decidir.
     */
    private final PublicacionMuroFinder publicacionMuroFinder;
    /** D-87: para saltear los habitos que el aprendiz pauso. */
    private final LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final RegistroPoliticasHabito politicas;

    public RegistroService(LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                            LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                            LoadPreferenciaHorarioPort loadPreferenciaPort,
                            ConsultarProgresoParticipanteHabitsPort progresoPort, AjustarPuntosPort ajustarPuntosPort,
                            PublicacionMuroFinder publicacionMuroFinder,
                            LoadDesbloqueoHabitoPort loadDesbloqueoPort,
                            ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator,
                            List<PoliticaHabito> politicas) {
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.progresoPort = progresoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.publicacionMuroFinder = publicacionMuroFinder;
        this.loadDesbloqueoPort = loadDesbloqueoPort;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
        // Se indexa UNA vez, en el arranque: en `completar` la resolucion es un lookup de
        // mapa, sin streams ni asignaciones (CLAUDE.MD §5.4.7, hot path).
        this.politicas = new RegistroPoliticasHabito(politicas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroHabito> consultar(UserId actorId, UserId participanteId, LocalDate fecha) {
        requireSelf(actorId, participanteId);
        return loadRegistroPort.porParticipanteYFecha(participanteId, fecha);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrosDelDia consultarEnSuZona(UserId actorId, UserId participanteId, LocalDate fecha) {
        ProgresoParticipanteHabits progreso = requireSelf(actorId, participanteId);
        return new RegistrosDelDia(loadRegistroPort.porParticipanteYFecha(participanteId, fecha), fecha,
                ZoneId.of(progreso.timezone()), progreso.fechaInicio());
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrosDelDia consultarHoy(UserId actorId, UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireSelf(actorId, participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoyEnSuZona = clock.now().atZone(zona).toLocalDate();
        return new RegistrosDelDia(loadRegistroPort.porParticipanteYFecha(participanteId, hoyEnSuZona), hoyEnSuZona,
                zona, progreso.fechaInicio());
    }

    @Override
    @Transactional
    public List<RegistroHabito> generar(UserId participanteId, LocalDate fecha) {
        return generarInterno(requireProgreso(participanteId), participanteId, fecha, null);
    }

    /**
     * Ver javadoc del puerto: el dia COMPLETO de hoy en su zona, salvo el primer dia del programa, en que se descarta
     * lo que ya no se puede completar a esta hora (decision del dueño del 2026-09-02).
     *
     * <p><b>Corregido 2026-10-06 (D-259).</b> Antes descartaba por hora TODOS los dias. Con la regla del dueño
     * («un habito se puede registrar durante su dia aunque se le haya pasado la hora») eso perdia habitos: un dia
     * armado tarde —la red de seguridad de {@code GET /habit-tracks/today}, o el barrido horario que llega pasadas
     * las 02:00 locales porque el backend estuvo caido— no generaba los de hora ya cerrada, y la persona no tenia
     * como registrarlos aunque los hubiera hecho.
     */
    @Override
    @Transactional
    public List<RegistroHabito> generarDisponiblesAhora(UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        var ahoraEnSuZona = clock.now().atZone(zona);
        LocalDate hoy = ahoraEnSuZona.toLocalDate();
        LocalTime horaDeCorte = esSuPrimerDia(progreso, hoy) ? ahoraEnSuZona.toLocalTime() : null;
        return generarInterno(progreso, participanteId, hoy, horaDeCorte);
    }

    /**
     * El primer dia del programa ({@code fecha_inicio}): el unico en que la decision del 2026-09-02 sigue diciendo
     * que no se generan los habitos cuya hora ya cerro. Es el dia del staff que activa su seguimiento a media tarde
     * ({@code activarSeguimientoPersonal}: dia 1 = hoy). Sin fecha de inicio conocida no es primer dia.
     */
    private static boolean esSuPrimerDia(ProgresoParticipanteHabits progreso, LocalDate hoy) {
        return hoy.equals(progreso.fechaInicio());
    }

    /** Ver javadoc del puerto: jornada completa (sin corte de hora) para HOY en la zona del participante. */
    @Override
    @Transactional
    public List<RegistroHabito> generarDiaCompletoEnSuZona(UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoyEnSuZona = clock.now().atZone(zona).toLocalDate();
        return generarInterno(progreso, participanteId, hoyEnSuZona, null);
    }

    /**
     * {@code horaDeCorte} nulo = generar el dia completo (uso del barrido nocturno, que corre
     * cuando el dia todavia no empezo). No nulo = solo lo que sigue siendo alcanzable.
     *
     * <p>El progreso llega por parametro (V-5, D-180): los tres llamadores ya lo habian leido, y
     * leerlo de nuevo aca era una consulta mas por participante en cada barrido y en cada
     * {@code GET /habit-tracks/today} sin registros.
     */
    private List<RegistroHabito> generarInterno(ProgresoParticipanteHabits progreso, UserId participanteId,
                                                LocalDate fecha, LocalTime horaDeCorte) {
        TipoDia tipoDia = resolverTipoDia(fecha);
        Instant ahora = clock.now();

        List<Habito> catalogo = new ArrayList<>(loadHabitoPort.catalogoActivo());
        catalogo.addAll(loadHabitoPort.personalesActivosDe(participanteId));
        // V-5 (D-180): los horarios de todo el catalogo en UNA consulta de lote, no una por habito.
        Map<HabitoId, HorariosDelHabito> horariosPorHabito = horariosDe(catalogo);
        List<DesbloqueoHabito> plan = loadDesbloqueoPort.deParticipante(participanteId);
        // D-196 y D-200: un desbloqueo o un horario que arranca por encima del dia de hoy no deja
        // el habito afuera si YA CORRIO (retroceso de dia). Es UNA consulta para los dos, y solo si
        // hay algun candidato.
        Map<HabitoId, Integer> yaGenerados = diasMasAltosYaGenerados(participanteId,
                habitosQueDependenDeSiYaCorrieron(progreso.diaPrograma(), tipoDia, plan, horariosPorHabito));

        // D-87: los habitos que este aprendiz PAUSO no generan track. Se resuelve en UNA consulta
        // y no una por habito — el barrido nocturno recorre todo el padron.
        //
        // Se saltean tambien los que el aprendiz eligio para MAS ADELANTE (`dia_desbloqueo` en el
        // futuro): sin esto, elegir un habito "para el dia 2" guardaba el numero y no cambiaba
        // nada, el habito empezaba a generar track esa misma noche igual.
        //
        // Compatibilidad hacia atras, deliberada: solo se saltea lo que tiene fila y esta
        // PAUSADO o todavia no le toca. Un habito sin fila en `desbloqueos_habito` se sigue
        // generando como siempre. Filtrar por "esta en el plan" habria dejado a TODO el padron
        // sin habitos de un dia para el otro, porque hoy esa tabla esta vacia para todos.
        Set<HabitoId> fueraDelPlanDeHoy = fueraDelPlanDelDia(progreso, fecha, plan, yaGenerados);

        // V38: los que el aprendiz apago para ESE dia. Se suman al mismo conjunto de descarte
        // porque responden la misma pregunta que la pausa y el dia de desbloqueo — "¿va hoy?" —, y
        // asi el bucle de abajo sigue teniendo un solo lugar donde mirar.
        fueraDelPlanDeHoy.addAll(loadPreferenciaPort.habitosApagadosEn(participanteId, fecha));
        // V40: y los que apago para ESE dia de la semana, todas las semanas. `fecha` ya viene en la
        // zona del participante, asi que el dia sale de ella y NO se recalcula en ningun otro lado
        // — ahi es por donde volveria a entrar E-91.
        fueraDelPlanDeHoy.addAll(
                loadPreferenciaPort.habitosApagadosEnDiaSemana(participanteId, fecha.getDayOfWeek()));

        // Las preferencias se cargan SIEMPRE, tambien con `horaDeCorte` nulo.
        //
        // Antes el barrido nocturno recibia un mapa vacio, y era inocuo mientras lo unico que se
        // hacia con el fuera descartar por hora de cierre (`sigueAlcanzable` no filtra nada sin
        // corte). Dejo de serlo el dia que el horario por fecha empezo a decidir cosas: el cron de
        // las 05:02 UTC es la via por la que se generan los tracks de TODO el padron, asi que un
        // horario que solo se lee en el camino a demanda funciona probandolo a mano por HTTP y no
        // funciona en produccion para nadie.
        var preferencias = loadPreferenciaPort.porParticipanteHabitosYFecha(participanteId,
                        catalogo.stream().map(Habito::id).toList(), fecha).stream()
                        .collect(Collectors.toMap(PreferenciaHorario::habitoId, p -> p));
        // V-5 (D-180): lo que ya existe ese dia, como los horarios, en UNA consulta de lote y no en
        // una por habito. Con ~25 habitos eran ~50 consultas por participante, y
        // `GET /habit-tracks/today` las repetia en cada pedido mientras el dia siguiera sin
        // registros (la red de seguridad de TracksDelDiaProyeccionService). La decision es la
        // misma de antes, habito por habito: solo cambia de donde sale el dato.
        Set<HabitoId> conRegistroEseDia = habitosConRegistro(participanteId, fecha);
        List<RegistroHabito> generados = new ArrayList<>();
        for (Habito habito : catalogo) {
            if (fueraDelPlanDeHoy.contains(habito.id())) {
                continue;
            }
            if (conRegistroEseDia.contains(habito.id())) {
                continue; // idempotente: ya existe (UNIQUE participante+habito+fecha)
            }
            // D-200: un habito que ya corrio y quedo por debajo del inicio de su horario se resuelve
            // como en ese primer dia. El registro guarda igual el dia REAL (snapshot, abajo).
            HorariosDelHabito horarios = horariosPorHabito.getOrDefault(habito.id(), SIN_HORARIOS);
            int diaDelHabito = horarios.diaEfectivo(progreso.diaPrograma(), tipoDia, yaGenerados.get(habito.id()));
            boolean aplicaHoy = horarios.vigentesEn(diaDelHabito, tipoDia).stream()
                    .anyMatch(h -> sigueAlcanzable(h, preferencias.get(habito.id()), horaDeCorte));
            if (!aplicaHoy) {
                continue;
            }
            // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
            // D-169: `es_opcional` es el del DIA (Ciclos de Intoxicacion), con el MISMO `diaPrograma`
            // que se guarda en el registro: el snapshot no se contradice a si mismo.
            RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(idGenerator.newId()), participanteId,
                    habito.id(), fecha, progreso.diaPrograma(), tipoDia,
                    habito.esOpcionalEnDia(progreso.diaPrograma()), ahora);
            // Idempotente tambien con pedidos simultaneos (E-230): si otro pedido lo creo entre la
            // consulta de arriba y este INSERT, no se revienta con la UNIQUE; simplemente ya existe.
            if (saveRegistroPort.insertarSiNoExiste(registro)) {
                generados.add(registro);
            }
            conRegistroEseDia.add(habito.id());
        }
        return generados;
    }

    /**
     * Los habitos del plan que ese dia NO van: pausados ese dia, o cuyo dia de desbloqueo no llego.
     *
     * <p>{@code estaPausadoEl(fecha)} y no {@code estaPausado()}: desde V31 una pausa puede tener
     * fecha de fin, y pasada esa fecha el habito vuelve a generarse SOLO — la reanudacion se deriva
     * del calendario, no la ejecuta ningun cron. La zona entra por parametro desde 2026-09-07: la
     * pausa tambien tiene extremo de ABAJO ({@code pausadoEn}), y sin el apagaba retroactivamente
     * todos los dias anteriores.
     *
     * <p><b>D-196:</b> un desbloqueo por encima del dia de hoy ya no alcanza para dejar el habito
     * afuera: si el habito YA CORRIO (retroceso de dia, ver
     * {@link DesbloqueoHabito#todaviaNoLeToca}), sigue.
     */
    private Set<HabitoId> fueraDelPlanDelDia(ProgresoParticipanteHabits progreso, LocalDate fecha,
                                             List<DesbloqueoHabito> plan, Map<HabitoId, Integer> yaGenerados) {
        ZoneId zona = ZoneId.of(progreso.timezone());
        int dia = progreso.diaPrograma();
        return plan.stream()
                .filter(d -> d.estaPausadoEl(fecha, zona) || d.todaviaNoLeToca(dia, yaGenerados.get(d.habitoId())))
                .map(DesbloqueoHabito::habitoId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Los habitos para los que "¿ya corrio?" cambia la decision de hoy: los del plan con un
     * desbloqueo por encima del dia (D-196) y los que ningun horario cubre solo porque todos
     * arrancan despues (D-200). Es la excepcion —un retroceso, o alguien antes del dia en que
     * arranca un habito del catalogo—, y fuera de ella no se lee ningun registro.
     */
    private static List<HabitoId> habitosQueDependenDeSiYaCorrieron(int dia, TipoDia tipoDia,
                                                                    List<DesbloqueoHabito> plan,
                                                                    Map<HabitoId, HorariosDelHabito> horarios) {
        var porDesbloqueo = plan.stream().filter(d -> d.diaDesbloqueo() > dia).map(DesbloqueoHabito::habitoId);
        var porHorario = horarios.entrySet().stream()
                .filter(e -> e.getValue().necesitaSaberSiYaCorrio(dia, tipoDia))
                .map(Map.Entry::getKey);
        return Stream.concat(porDesbloqueo, porHorario).distinct().toList();
    }

    /** El {@code dia_programa} mas alto ya generado por habito (D-196), en UNA consulta y solo si hace falta. */
    private Map<HabitoId, Integer> diasMasAltosYaGenerados(UserId participanteId, List<HabitoId> habitos) {
        return habitos.isEmpty() ? Map.of()
                : loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participanteId, habitos);
    }

    /** Los habitos que ya tienen registro ese dia, en UNA consulta (V-5). Mutable: el bucle suma los que inserta. */
    private Set<HabitoId> habitosConRegistro(UserId participanteId, LocalDate fecha) {
        return loadRegistroPort.porParticipanteYFecha(participanteId, fecha).stream()
                .map(RegistroHabito::habitoId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Los horarios de todo el catalogo del participante, en UNA consulta (V-5), agrupados por habito
     * y con el habito al lado: el primer dia de uno PERSONAL ya esta alcanzado (D-216, TZ-15).
     */
    private Map<HabitoId, HorariosDelHabito> horariosDe(List<Habito> catalogo) {
        return HorariosDelHabito.porHabito(catalogo,
                loadHorarioPort.porHabitos(catalogo.stream().map(Habito::id).toList()));
    }

    /**
     * Un horario sigue alcanzable si no tiene hora de cierre (el habito no vence dentro del
     * dia) o si esa hora todavia no paso. Con {@code horaDeCorte} nulo no se filtra nada:
     * es el caso del barrido nocturno, que genera el dia entero por adelantado.
     */
    private boolean sigueAlcanzable(HorarioHabito horario, PreferenciaHorario preferencia, LocalTime horaDeCorte) {
        var resuelto = HorarioResuelto.de(horario, preferencia);
        return horaDeCorte == null || resuelto.horaLimite() == null
                || resuelto.horaLimite().isAfter(horaDeCorte);
    }

    @Override
    @Transactional
    public RegistroHabito completar(CompletarRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        ProgresoParticipanteHabits progreso = requireSelf(command.actorId(), registro.participanteId());
        requireDeUnDiaQueNoTermino(registro, ZoneId.of(progreso.timezone()));
        Habito habito = requireHabito(registro.habitoId());
        requireMedicionAdmitida(habito, command.medicion());
        // La politica gobierna el GESTO GENERICO y solo ese — es literalmente la pregunta que
        // `puedeCompletarseDirecto` dice contestar. Un habito con gesto propio (hoy solo la Clase
        // Diaria) llega hasta aca a proposito, para no duplicar el calculo de puntos ni el de la
        // ventana; preguntarle a la politica por esa invocacion seria hacerle una pregunta que no
        // le corresponde, y le cerraria al habito su unico camino valido (E-120).
        if (command.gesto() == GestoCompletar.GENERICO) {
            requirePoliticaPermiteCompletarDirecto(habito, contextoDe(registro, command.medicion()));
        }

        Instant ahora = clock.now();
        VentanaEntrega ventana = resolverVentana(registro, habito);
        /* Aca vivia un `expirar()` + 409 "El habito expiro — no se puede completar". Se quita por
           pedido del dueno del proyecto, y la razon aguanta sola: registrar tarde es informacion,
           y perderla no ayuda a nadie. Quien se desperto a las 10 y lo anota a las 11 HIZO el
           habito; lo unico que no hizo fue llegar a tiempo, y eso ya se cobra donde corresponde
           -- `ResultadoOtorgamiento` devuelve 0 puntos en fase EXPIRADO. Bloquear ademas el
           registro cobraba dos veces por la misma tardanza.

           Con el throw desaparece tambien el motivo del `noRollbackFor` que tenia este metodo
           (C-9): ya no hay una escritura que salvar de su propia excepcion.

           D-259 (2026-10-06): lo tarde vale DENTRO del dia del registro. Un registro de un dia que ya
           termino se rechaza arriba, en `requireDeUnDiaQueNoTermino`, antes de tocar nada. */

        // D-97: un habito SIN horario (ni disparo ni cierre, ni de catalogo ni de preferencia —
        // hoy DESPERTAR) se evidencia con el solo hecho de registrarlo, y la hora de la accion es
        // su ancla: siempre esta a tiempo y paga el puntaje completo. Antes esta rama dejaba
        // `puntos = 0` ("sin ventana, el repo viejo nunca otorga puntos"), y el efecto era que
        // DESPERTAR se completaba y no pagaba nunca — el dueno lo definio al reves.
        int puntos = ResultadoOtorgamiento.PUNTOS_COMPLETOS;
        MotivoPuntos motivo = MotivoPuntos.HABIT_COMPLETED;
        if (ventana != null) {
            ResultadoOtorgamiento resultado = ResultadoOtorgamiento.calcular(ventana.instanteAncla(), ahora,
                    ventana.extension());
            puntos = resultado.puntos();
            motivo = switch (resultado.fase()) {
                case EXTENDIDO -> MotivoPuntos.HABIT_EXTENDED;
                // Fuera de plazo: 0 puntos y queda ETIQUETADO como tarde. Sin esta rama, una
                // entrega vencida se guardaria como HABIT_COMPLETED con 0 puntos y nadie podria
                // distinguirla despues de un habito que valia cero por otro motivo.
                case EXPIRADO -> MotivoPuntos.LATE_HABIT;
                default -> MotivoPuntos.HABIT_COMPLETED;
            };
        }

        registro.completar(puntos, command.respuestaTexto(), command.calificacionProductividad(), null,
                command.medicion(), ahora);
        RegistroHabito guardado = saveRegistroPort.save(registro);

        if (puntos > 0) {
            ajustarPuntosPort.ajustar(registro.participanteId(), motivo, puntos,
                    "Habito completado: " + habito.titulo());
        }
        events.publishEvent(new HabitoCompletadoEvent(guardado.id().value(), guardado.participanteId(),
                habito.id().value(), puntos, ahora));
        return guardado;
    }

    /**
     * D-259 (regla del dueño, 2026-10-06): un habito se registra durante SU dia —aunque se le haya pasado la hora, con
     * menos puntos o ninguno— y no despues. Antes este caso de uso completaba un registro de cualquier dia anterior,
     * PENDIENTE (si el barrido todavia no lo habia vencido) o EXPIRADO, con 0 puntos.
     *
     * <p>Va aca y no en {@link RegistroHabito#completar}: la racha sin celular y el Santuario completan su registro
     * por su propio gesto cuando el ciclo cruza la medianoche, y esos siguen valiendo. El dia es el de la zona del
     * participante (regla 02 §1): a las 04:30 UTC todavia es el dia anterior en Lima.
     */
    private void requireDeUnDiaQueNoTermino(RegistroHabito registro, ZoneId zona) {
        if (registro.suDiaYaTermino(zona, clock.now())) {
            throw new RegistroDeUnDiaCerradoException(registro.fechaEjecucion());
        }
    }

    /** La regla vive en {@link TipoDia#delDia(LocalDate)} — la comparte la lectura de horarios vigentes. */
    private TipoDia resolverTipoDia(LocalDate fecha) {
        return TipoDia.delDia(fecha);
    }

    /**
     * preferencia -&gt; horario del catalogo vigente para el dia de programa del registro (D-200: el
     * de su primer dia si el registro se genero por debajo del inicio de su horario).
     */
    private VentanaEntrega resolverVentana(RegistroHabito registro, Habito habito) {
        HorariosDelHabito horarios = HorariosDelHabito.de(loadHorarioPort.porHabito(habito.id()));
        int dia = horarios.diaEfectivoDeUnRegistro(registro.diaPrograma(), registro.tipoDia());
        HorarioHabito vigente = horarios.vigentesEn(dia, registro.tipoDia()).stream().findFirst().orElse(null);

        LocalTime horaDisparo = vigente != null ? vigente.horaDisparo() : null;
        LocalTime horaLimite = vigente != null ? vigente.horaLimite() : null;

        Optional<PreferenciaHorario> pref = loadPreferenciaPort.porParticipanteHabitoYFecha(registro.participanteId(),
                habito.id(), registro.fechaEjecucion());
        if (pref.isPresent()) {
            if (pref.get().horaDisparo() != null) {
                horaDisparo = pref.get().horaDisparo();
            }
            if (pref.get().horaLimite() != null) {
                horaLimite = pref.get().horaLimite();
            }
        }
        if (horaDisparo == null && horaLimite == null) {
            return null;
        }

        ProgresoParticipanteHabits progreso = requireProgreso(registro.participanteId());
        ZoneId zona = ZoneId.of(progreso.timezone());
        return VentanaEntrega.calcular(registro.fechaEjecucion(), horaDisparo, horaLimite, zona,
                habito.horasExtraEvidencia());
    }

    /**
     * Pertenencia Y estado de cuenta, en una sola guard clause al principio de cada caso de
     * uso. Antes el chequeo de suspension vivia dentro de {@code resolverVentana}, que
     * retorna temprano cuando el habito no tiene horario ni preferencia — con los datos
     * reales de hoy (ningun habito tiene fila en `horarios_habito`) esa rama era la comun,
     * asi que un aprendiz SUSPENDIDO podia operar igual. La autorizacion no puede depender
     * de si el habito tiene horario configurado.
     */
    /**
     * Un habito con regla propia (Santuario y los que vengan) se completa por su propio
     * gesto, no por este. La regla la aporta su politica; este servicio solo la consulta
     * y traduce el rechazo a HTTP — asi agregar el proximo habito especial no lo toca.
     *
     * <p>El {@code switch} sobre {@link DecisionPolitica} es exhaustivo por ser sellada:
     * si manana aparece una tercera variante, el compilador obliga a contemplarla aca.
     */
    private void requirePoliticaPermiteCompletarDirecto(Habito habito, ContextoCompletar contexto) {
        PoliticaHabito politica = politicas.para(habito);
        switch (politica.puedeCompletarseDirecto(habito, contexto)) {
            case DecisionPolitica.Procede ignorada -> {
                // sigue el camino compartido
            }
            case DecisionPolitica.NoProcede(String motivo) -> throw new IllegalArgumentException(motivo);
        }
    }

    /**
     * Los hechos externos que alguna politica puede necesitar, PEREZOSOS: el lambda no corre
     * salvo que la politica de este habito pregunte, y hoy pregunta una sola de las tres
     * (ver {@link ContextoCompletar}). Sin esa pereza, cada completacion del dia pagaria una
     * consulta al Muro que casi nadie usa.
     */
    private ContextoCompletar contextoDe(RegistroHabito registro, MedicionDiaria medicion) {
        return ContextoCompletar.de(() -> publicoEnElMuroEseDia(registro), medicion);
    }

    /**
     * D-226: un numero solo entra en un habito que mide algo (su politica declara unidad). En los
     * demas se rechaza en vez de guardarlo: nadie lo leeria, y el dia que ese habito empiece a medir
     * encontraria numeros viejos de origen desconocido. Vale para todos los gestos, no solo el
     * generico: es una regla sobre el dato, no sobre el camino.
     */
    private void requireMedicionAdmitida(Habito habito, MedicionDiaria medicion) {
        if (medicion != null && politicas.para(habito).unidadDeMedicion().isEmpty()) {
            throw new IllegalArgumentException("Este hábito no registra un número: complétalo sin valorMedido");
        }
    }

    /**
     * El dia del registro se abre y se cierra en la zona del PARTICIPANTE, no en UTC ni en la
     * del servidor (regla 02-tiempo-zonas-y-schedulers, bug E-91): para alguien en Lima
     * (UTC-5), una publicacion de las 02:00 UTC pertenece al dia ANTERIOR, y contarla contra
     * el dia de hoy le regalaria el habito con el post de ayer.
     *
     * <p>Se ancla en {@code fechaEjecucion} del registro y no en "hoy": el registro es de un
     * dia concreto, y ese es el dia que hay que comprobar aunque se complete un rato despues
     * (la ventana de gracia/extension puede cruzar la medianoche).
     *
     * <p>Media ventana {@code [inicio, inicio+1dia)} — sin hueco ni solape entre dos dias
     * consecutivos.
     */
    private boolean publicoEnElMuroEseDia(RegistroHabito registro) {
        ZoneId zona = ZoneId.of(requireProgreso(registro.participanteId()).timezone());
        LocalDate dia = registro.fechaEjecucion();
        return publicacionMuroFinder.publicoEntre(registro.participanteId(), dia.atStartOfDay(zona).toInstant(),
                dia.plusDays(1).atStartOfDay(zona).toInstant());
    }

    /** Devuelve el progreso ya leido para que el llamador no tenga que volver a pedirlo (V-5). */
    private ProgresoParticipanteHabits requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar sobre sus habitos");
        }
        return requireProgreso(participanteId);
    }

    /**
     * Carga CON BLOQUEO: todos sus llamadores mutan el registro y otorgan puntos. Sin el
     * lock, dos requests concurrentes leen ambas el mismo estado PENDIENTE, ambas pasan la
     * validacion del dominio y ambas pagan (verificado en vivo: 6 llamadas paralelas
     * devolvian 200 cada una, la 7a secuencial devolvia 409).
     *
     * <p><b>Invariante para quien llame a {@link #completar}</b> (hallazgo de seguridad del
     * 2026-09-21): esta tiene que ser la PRIMERA lectura de esa fila en la transaccion, o al
     * menos la primera que no venga ya bloqueada. Si el llamador materializo antes el registro
     * con una consulta sin cerrojo —y se une a esta transaccion, que es lo que hace un
     * {@code @Transactional} REQUIRED dentro de un {@code @ApplicationModuleListener}—, el
     * contexto de persistencia ya tiene la entidad y Hibernate NO la rehidrata: la consulta con
     * cerrojo se ejecuta, trae la fila fresca y la descarta, y este metodo devuelve el estado
     * viejo. El cerrojo se toma igual, pero protege la escritura y no la decision. Los caminos
     * que entran por el {@code POST /complete} cumplen el invariante solos, porque no leen la
     * fila antes; los tres que la buscan por (participante, habito, dia) —el oyente del Muro,
     * {@code ClaseDiariaHabitoService} y {@code PastillaRenacerHabitoService}— lo cumplen
     * leyendo con {@code LoadRegistroHabitoPort.porParticipanteHabitoYFechaParaEscritura}.
     *
     * <p>Y no alcanza con envolver al llamador en un {@code REQUIRES_NEW}: eso cambia CUAL es la
     * transaccion compartida, no que las dos lecturas la compartan — es el caso de
     * {@code PastillaRenacerHabitoService}, que corre dentro de la transaccion propia de
     * {@code EspirituService.reflejarEnPastillaRenacer} y sufria el problema igual.
     */
    private RegistroHabito requireRegistro(RegistroHabitoId id) {
        return loadRegistroPort.byIdParaEscritura(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    private Habito requireHabito(HabitoId id) {
        return loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
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
