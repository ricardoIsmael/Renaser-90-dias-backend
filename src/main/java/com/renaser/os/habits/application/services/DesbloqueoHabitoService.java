package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.SaveDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.application.ports.out.registro.RetirarObligacionesPausadasPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Hueco #12 — lectura del plan de desbloqueo escalonado (ver javadoc de
 * {@link ConsultarDesbloqueosHabitoUseCase} para lo que sigue sin portarse, D-H2) MAS el alta
 * autoservicio simple de {@link ElegirHabitoUseCase}: el aprendiz agrega un habito del catalogo
 * a su plan. Esto ultimo NO es el algoritmo de escalonamiento por lotes del repo viejo.
 */
@Service
public class DesbloqueoHabitoService implements ConsultarDesbloqueosHabitoUseCase, ElegirHabitoUseCase,
        CambiarEstadoHabitoDelPlanUseCase, QuitarHabitoDelPlanUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadDesbloqueoHabitoPort loadPort;
    private final SaveDesbloqueoHabitoPort savePort;
    private final LoadHabitoPort loadHabitoPort;
    private final RetirarObligacionesPausadasPort retirarPort;
    /** Issue #76: al reactivar, el registro de hoy que la pausa borro vuelve con la regla de siempre del armado del dia. */
    private final GenerarTracksDelDiaUseCase generarTracks;
    private final Clock clock;

    public DesbloqueoHabitoService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                    LoadDesbloqueoHabitoPort loadPort, SaveDesbloqueoHabitoPort savePort,
                                    LoadHabitoPort loadHabitoPort,
                                    RetirarObligacionesPausadasPort retirarPort,
                                    GenerarTracksDelDiaUseCase generarTracks, Clock clock) {
        this.generarTracks = generarTracks;
        this.progresoPort = progresoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.loadHabitoPort = loadHabitoPort;
        this.retirarPort = retirarPort;
        this.clock = clock;
    }

    @Override
    public PlanDesbloqueo consultar(UserId actorId) {
        requireProgreso(actorId);
        List<ItemDesbloqueo> items = loadPort.deParticipante(actorId).stream()
                .map(d -> new ItemDesbloqueo(d.habitoId(), d.diaDesbloqueo(), d.elegidoEn(), d.estaPausado(),
                        d.pausadoHasta()))
                .toList();
        return new PlanDesbloqueo(!items.isEmpty(), items);
    }

    /**
     * Idempotente (ver javadoc de {@link SaveDesbloqueoHabitoPort#elegirSiFalta}): elegir dos
     * veces el mismo habito no falla ni duplica, simplemente devuelve el mismo resultado.
     *
     * <p>Admite habitos del catalogo y habitos PERSONAL del propio aprendiz — ver
     * {@link #requirePuedeEntrarEnElPlan} para por que dejo de rechazar los personales (E-138).
     *
     * <p>Consecuencia de esa idempotencia: volver a elegir el mismo habito con OTRO
     * {@code diaDesbloqueo} tampoco lo mueve — el INSERT no hace nada y se relee la fila que ya
     * estaba. Para cambiar el dia hay que sacar el habito del plan ({@code quitar}) y volver a
     * elegirlo. Es deliberado: mover el dia de un habito ya empezado es otra operacion, con
     * otras consecuencias sobre los registros ya generados.
     */
    @Override
    @Transactional
    public DesbloqueoHabito elegir(ElegirHabitoCommand command) {
        return elegir(command, clock.now());
    }

    @Override
    @Transactional
    public DesbloqueoHabito elegir(ElegirHabitoCommand command, Instant ahora) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        // D-103: el dia 0 YA NO se rechaza. Con la regla "hoy se organiza manana" (D-91/D-98), quien
        // eligio empezar manana tiene que poder armar su plan hoy — y el interruptor ACTIVO/PAUSADO
        // pasa por aca primero (D-99), asi que el 403 de antes lo dejaba sin interruptor el dia
        // previo a empezar. Lo que se elige en dia 0 arranca el dia 1: `resolverDiaDesbloqueo`.
        // Spec 023: el rango de desbloqueo es el de SU programa (90 o 365 dias), no un 90 escrito de antemano.
        if (progreso.diaPrograma() > progreso.duracionDias()) {
            throw new IllegalArgumentException("El día de programa está fuera del rango de desbloqueo (1-" + progreso.duracionDias() + ")");
        }
        Habito habito = requireHabito(command.habitoId());
        habito.exigirQueSeaVisibleEn(progreso.formacion());
        requirePuedeEntrarEnElPlan(habito, command.actorId());

        int diaDesbloqueo = resolverDiaDesbloqueo(command.diaDesbloqueo(), progreso.diaPrograma(), progreso.duracionDias());
        savePort.elegirSiFalta(command.actorId(), command.habitoId(), diaDesbloqueo, ahora, ahora);
        return loadPort.deParticipanteYHabito(command.actorId(), command.habitoId())
                .orElseThrow(() -> new IllegalStateException(
                        "Desbloqueo no encontrado tras asegurar su existencia: " + command.habitoId()));
    }

    /**
      * D-87: el interruptor ACTIVO/PAUSADO de Plan, que hasta ahora no tenia backend al que
      * llamar. Escribe `desbloqueos_habito.pausado_en` — SOLO de este aprendiz —, nunca
      * `habitos.activo`, que es del catalogo compartido y es de admin.
      */
    @Override
    @Transactional
    public DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command) {
        return cambiarEstado(command, clock.now());
    }

    @Override
    @Transactional
    public DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command, Instant referencia) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        habito.exigirQueSeaVisibleEn(progreso.formacion());
        DesbloqueoHabito desbloqueo = loadPort.deParticipanteYHabito(command.actorId(), command.habitoId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Este hábito no está en tu plan: " + command.habitoId()));

        if (command.activo()) {
            desbloqueo.reactivar(referencia);
        } else {
            ZonedDateTime ahora = referencia.atZone(ZoneId.of(progreso.timezone()));
            desbloqueo.pausar(habito.desactivable(), command.pausadoHasta(), ahora);
            /* Pausar apagaba la generacion FUTURA, pero el track de HOY ya estaba creado -- lo
               hace el barrido de las 05:02 o la primera apertura de la app -- y se quedaba en
               PENDIENTE. La persona apagaba el habito a las 10:00, lo seguia viendo en su dia y
               en evidencias, y a la noche el barrido lo marcaba fallado. El boton decia "solo
               hoy" y hoy contaba igual.

               El dia de corte sale del reloj EN LA ZONA DEL PARTICIPANTE, no del servidor: con
               `LocalDate.now()` el borrado se corre una casilla para quien esta en otro huso, que
               es E-91 entrando por la puerta de al lado.

               Solo se retira lo que sigue ABIERTO. Ver el javadoc del puerto: borrar lo ya
               vencido dejaria limpiar fallos pausando despues de fallar. */
            LocalDate desdeHoy = ahora.toLocalDate();
            retirarPort.retirarPendientes(command.actorId(), command.habitoId(), desdeHoy,
                    command.pausadoHasta());
        }
        DesbloqueoHabito guardado = savePort.save(desbloqueo);
        if (command.activo()) {
            reponerElDeHoy(command.actorId(), command.habitoId());
        }
        return guardado;
    }

    /**
     * Issue #76: pausar borra el registro PENDIENTE de hoy (arriba), y reactivar solo limpiaba la pausa: el habito
     * volvia a estar «activo» pero sin su registro de hoy, y no se veia en Training hasta manana. Se vuelve a pedir el
     * de hoy con la regla de siempre del armado del dia: si hoy sigue sin tocarle (el programa no empezo, no es uno de
     * sus dias, lo apago para hoy, esta elegido para mas adelante) no crea nada, y si ya existe no lo duplica. Va DESPUES
     * de guardar la fila, porque el armado lee la pausa.
     */
    private void reponerElDeHoy(UserId actorId, HabitoId habitoId) {
        generarTracks.generarElDeHoyDe(actorId, List.of(habitoId));
    }

    /**
     * D-87: faltaba la contraparte de {@link #elegir} — se podia agregar al plan pero no sacar,
     * asi que un plan solo crecia. Idempotente a proposito: quitar algo que no esta no es un
     * error, es el estado que el llamador queria.
     */
    @Override
    @Transactional
    public void quitar(QuitarHabitoCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        habito.exigirQueSeaVisibleEn(progreso.formacion());
        if (!habito.desactivable()) {
            throw new IllegalStateException("Este hábito es obligatorio y no se puede sacar de tu plan");
        }
        savePort.borrar(command.actorId(), command.habitoId());
    }

    /**
     * Sin dia pedido, el habito arranca hoy — el comportamiento historico, y el unico que
     * existia antes de que el comando llevara el dia. Con dia pedido, solo hacia adelante: los
     * dias ya vividos ya generaron (o no) sus registros, y desbloquear hacia atras le mentiria
     * a la coherencia de esos dias.
     *
     * <p>El tope es la duracion del programa de la persona (90 o 365; spec 023). El comando solo acota al maximo posible (`@Max(365)`):
     * aca se compara con la suya, y se repite porque el dia ACTUAL puede ser mayor que el pedido y ese caso no lo cubre ninguna anotacion.
     */
    private static int resolverDiaDesbloqueo(Integer pedido, int diaActual, int duracionDias) {
        // Dia 0 = todavia no empezo: lo mas temprano que existe es el dia 1.
        if (pedido != null && pedido > duracionDias) {
            throw new IllegalArgumentException("El día de desbloqueo no puede pasar del último día de tu programa (" + duracionDias + ")");
        }
        int primerDiaPosible = Math.max(1, diaActual);
        if (pedido == null) {
            return primerDiaPosible;
        }
        if (pedido < primerDiaPosible) {
            throw new IllegalArgumentException("No puedes desbloquear un hábito para un día que ya pasó — "
                    + "el más temprano que puedes elegir es el día " + primerDiaPosible);
        }
        return pedido;
    }

    /**
     * Que habitos puede meter un aprendiz en SU plan: los del catalogo compartido, y los PERSONAL
     * que sean SUYOS. En los dos casos tiene que estar activo.
     *
     * <p><b>E-138: antes esto rechazaba TODO habito PERSONAL</b> con
     * {@code "Solo se eligen habitos del catalogo, no habitos personales"}. La frase describia
     * bien lo que significaba "elegir" cuando se escribio (sacar algo de un catalogo compartido),
     * pero desde D-87/V23 esta tabla dejo de ser solo eso: {@code desbloqueos_habito} pasa a ser
     * <i>"que habitos lleva este aprendiz en su plan"</i> y es el UNICO lugar donde vive el
     * interruptor ACTIVO/PAUSADO ({@code pausado_en}/{@code pausado_hasta}, V31). Como el movil
     * asegura la fila con este caso de uso antes de mandar el PATCH (D-99), el rechazo dejaba a
     * los habitos propios sin interruptor: 400 al primer toque. La tabla `habitos` no tiene donde
     * guardar esa pausa —su unica bandera es `activo`, que para un habito PERSONAL lo esconde
     * entero de {@code GET /api/v1/habits} (baja logica, no pausa) y ademas no sabe expresar
     * "hasta el domingo"—, asi que darle su propio mecanismo habria dejado dos tablas
     * respondiendo la misma pregunta, que es justo lo que V23 y V31 argumentan evitar.
     *
     * <p>Lo que el rechazo SI protegia y aca se conserva explicito: el habito personal de OTRO
     * aprendiz. Se responde 404 y no 403 a proposito — un habito ajeno no deberia ni existir para
     * quien pregunta, y un 403 confirmaria que ese id es real.
     */
    private static void requirePuedeEntrarEnElPlan(Habito habito, UserId actorId) {
        if (!habito.esDeSistema() && !habito.esPersonalDe(actorId)) {
            throw new NoSuchElementException("Hábito no encontrado: " + habito.id());
        }
        if (!habito.activo()) {
            throw new IllegalArgumentException(habito.esDeSistema()
                    ? "Este hábito no está activo en el catálogo"
                    : "Este hábito personal ya no está activo");
        }
    }

    private Habito requireHabito(HabitoId id) {
        return loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + id));
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
