package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.RegistroPoliticasHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Servicio del agregado `registro/` — el corazon del modulo. Integra
 * sincronicamente con `points.api.AjustarPuntosPort` DENTRO de la misma
 * transaccion que completa el registro (CLAUDE.MD §9.1: la misma garantia
 * atomica que ya usa `points`, no un evento).
 */
@Service
public class RegistroService implements ConsultarTracksDelDiaUseCase, GenerarTracksDelDiaUseCase,
        CompletarRegistroUseCase, ExpirarRegistrosVencidosUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);

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
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para el barrido nocturno de {@link #expirarPendientesAnterioresA}:
     * cada fila se guarda en su propia transaccion, aislada de las demas (C-6). No se usa
     * para nada mas — {@link #completar} resuelve C-9 con {@code noRollbackFor}, no con esto,
     * porque acá el registro ya viene bajo bloqueo pesimista de la MISMA transaccion en curso
     * (ver javadoc de {@link #requireRegistro}): abrir una segunda transaccion sobre la fila
     * bloqueada por la primera, sin haberla liberado, es un auto-interbloqueo entre dos
     * conexiones del mismo pool — nunca REQUIRES_NEW sobre una fila ya bloqueada por la
     * transaccion en curso.
     */
    private final TransactionTemplate transaccionPropia;

    public RegistroService(LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                            LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                            LoadPreferenciaHorarioPort loadPreferenciaPort,
                            ConsultarProgresoParticipanteHabitsPort progresoPort, AjustarPuntosPort ajustarPuntosPort,
                            PublicacionMuroFinder publicacionMuroFinder,
                            LoadDesbloqueoHabitoPort loadDesbloqueoPort,
                            ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator,
                            List<PoliticaHabito> politicas, PlatformTransactionManager transactionManager) {
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
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public List<RegistroHabito> consultar(UserId actorId, UserId participanteId, LocalDate fecha) {
        requireSelf(actorId, participanteId);
        return loadRegistroPort.porParticipanteYFecha(participanteId, fecha);
    }

    @Override
    @Transactional
    public List<RegistroHabito> generar(UserId participanteId, LocalDate fecha) {
        return generarInterno(participanteId, fecha, null);
    }

    /** Ver javadoc del puerto: descarta lo que ya no se puede completar a esta hora. */
    @Override
    @Transactional
    public List<RegistroHabito> generarDisponiblesAhora(UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        var ahoraEnSuZona = clock.now().atZone(zona);
        return generarInterno(participanteId, ahoraEnSuZona.toLocalDate(), ahoraEnSuZona.toLocalTime());
    }

    /** Ver javadoc del puerto: jornada completa (sin corte de hora) para HOY en la zona del participante. */
    @Override
    @Transactional
    public List<RegistroHabito> generarDiaCompletoEnSuZona(UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoyEnSuZona = clock.now().atZone(zona).toLocalDate();
        return generarInterno(participanteId, hoyEnSuZona, null);
    }

    /**
     * {@code horaDeCorte} nulo = generar el dia completo (uso del barrido nocturno, que corre
     * cuando el dia todavia no empezo). No nulo = solo lo que sigue siendo alcanzable.
     */
    private List<RegistroHabito> generarInterno(UserId participanteId, LocalDate fecha, LocalTime horaDeCorte) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        TipoDia tipoDia = resolverTipoDia(fecha);
        Instant ahora = clock.now();

        List<Habito> catalogo = new ArrayList<>(loadHabitoPort.catalogoActivo());
        catalogo.addAll(loadHabitoPort.personalesActivosDe(participanteId));

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
        Set<HabitoId> fueraDelPlanDeHoy = loadDesbloqueoPort.deParticipante(participanteId).stream()
                .filter(d -> d.estaPausado() || d.diaDesbloqueo() > progreso.diaPrograma())
                .map(DesbloqueoHabito::habitoId)
                .collect(java.util.stream.Collectors.toSet());

        List<RegistroHabito> generados = new ArrayList<>();
        for (Habito habito : catalogo) {
            if (fueraDelPlanDeHoy.contains(habito.id())) {
                continue;
            }
            if (loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), fecha).isPresent()) {
                continue; // idempotente: ya existe (UNIQUE participante+habito+fecha)
            }
            boolean aplicaHoy = loadHorarioPort.porHabito(habito.id()).stream()
                    .filter(h -> h.aplicaEnDia(progreso.diaPrograma(), tipoDia))
                    .anyMatch(h -> sigueAlcanzable(h, horaDeCorte));
            if (!aplicaHoy) {
                continue;
            }
            // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
            RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(idGenerator.newId()), participanteId,
                    habito.id(), fecha, progreso.diaPrograma(), tipoDia, habito.esOpcional(), ahora);
            generados.add(saveRegistroPort.save(registro));
        }
        return generados;
    }

    /**
     * Un horario sigue alcanzable si no tiene hora de cierre (el habito no vence dentro del
     * dia) o si esa hora todavia no paso. Con {@code horaDeCorte} nulo no se filtra nada:
     * es el caso del barrido nocturno, que genera el dia entero por adelantado.
     */
    private boolean sigueAlcanzable(HorarioHabito horario, LocalTime horaDeCorte) {
        return horaDeCorte == null || horario.horaLimite() == null
                || horario.horaLimite().isAfter(horaDeCorte);
    }

    @Override
    @Transactional(noRollbackFor = RegistroExpiradoException.class)
    public RegistroHabito completar(CompletarRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro.habitoId());
        requirePoliticaPermiteCompletarDirecto(habito, contextoDe(registro));

        Instant ahora = clock.now();
        VentanaEntrega ventana = resolverVentana(registro, habito);
        if (ventana != null && ventana.vencida(ahora)) {
            // C-9: sin el noRollbackFor de arriba, este throw revierte el save de la linea
            // anterior y el registro queda PENDIENTE para siempre (el aprendiz reintenta y
            // vuelve a chocar con el mismo 409 hasta el cron de las 05:00). El tipo propio
            // (en vez de IllegalStateException a secas) acota el noRollbackFor a ESTE punto
            // exacto — no a los otros guard clauses de RegistroHabito.completar(), que si
            // deben revertir su escritura si fallan (ver javadoc de RegistroExpiradoException).
            registro.expirar(ahora);
            saveRegistroPort.save(registro);
            throw new RegistroExpiradoException("El habito expiro — no se puede completar");
        }

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
            motivo = resultado.fase() == FaseOtorgamiento.EXTENDIDO ? MotivoPuntos.HABIT_EXTENDED
                    : MotivoPuntos.HABIT_COMPLETED;
        }

        registro.completar(puntos, command.respuestaTexto(), command.calificacionProductividad(), null, ahora);
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
     * C-6: antes, todo el barrido corria en una unica transaccion — una fila corrupta en
     * la posicion 400 revertia las 399 anteriores, y a la noche siguiente pasaba lo mismo
     * (los registros nunca llegaban a expirar). Ahora cada fila se guarda en su propia
     * transaccion ({@link #transaccionPropia}, REQUIRES_NEW): si una falla, la excepcion
     * se atrapa aca, esa fila queda pendiente para el proximo barrido, y las demas siguen
     * su curso normal.
     */
    @Override
    public int expirarPendientesAnterioresA(LocalDate hoy) {
        List<RegistroHabito> vencidos = loadRegistroPort.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE, hoy);
        Instant ahora = clock.now();
        int expirados = 0;
        int fallidos = 0;
        for (RegistroHabito registro : vencidos) {
            try {
                expirarUnoEnTransaccionPropia(registro, ahora);
                expirados++;
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits] no se pudo expirar el registro {} en el barrido de {}: {}", registro.id(), hoy,
                        ex.toString());
            }
        }
        if (!vencidos.isEmpty()) {
            log.info(
                    "[habits] barrido de expiracion de registros ({}): {} expirado(s), {} fallido(s) de {} candidato(s)",
                    hoy, expirados, fallidos, vencidos.size());
        }
        return expirados;
    }

    private void expirarUnoEnTransaccionPropia(RegistroHabito registro, Instant ahora) {
        transaccionPropia.executeWithoutResult(status -> {
            registro.expirar(ahora);
            saveRegistroPort.save(registro);
        });
    }

    /** La regla vive en {@link TipoDia#delDia(LocalDate)} — la comparte la lectura de horarios vigentes. */
    private TipoDia resolverTipoDia(LocalDate fecha) {
        return TipoDia.delDia(fecha);
    }

    /** preferencia -&gt; horario del catalogo vigente para el dia de programa del registro. */
    private VentanaEntrega resolverVentana(RegistroHabito registro, Habito habito) {
        HorarioHabito vigente = loadHorarioPort.porHabito(habito.id()).stream()
                .filter(h -> h.aplicaEnDia(registro.diaPrograma(), registro.tipoDia()))
                .findFirst().orElse(null);

        LocalTime horaDisparo = vigente != null ? vigente.horaDisparo() : null;
        LocalTime horaLimite = vigente != null ? vigente.horaLimite() : null;

        Optional<PreferenciaHorario> pref = loadPreferenciaPort.porParticipanteYHabito(registro.participanteId(),
                habito.id());
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
    private ContextoCompletar contextoDe(RegistroHabito registro) {
        return ContextoCompletar.de(() -> publicoEnElMuroEseDia(registro));
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

    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar sobre sus habitos");
        }
        requireProgreso(participanteId);
    }

    /**
     * Carga CON BLOQUEO: todos sus llamadores mutan el registro y otorgan puntos. Sin el
     * lock, dos requests concurrentes leen ambas el mismo estado PENDIENTE, ambas pasan la
     * validacion del dominio y ambas pagan (verificado en vivo: 6 llamadas paralelas
     * devolvian 200 cada una, la 7a secuencial devolvia 409).
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
