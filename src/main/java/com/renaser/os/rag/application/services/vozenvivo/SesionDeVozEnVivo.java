package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.ConversacionEnVivo;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.MotivoDeCierre;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.SalidaDeVozEnVivo;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.SesionEnVivo;
import com.renaser.os.rag.application.ports.out.tiempo.ProgramarTareaPeriodicaPort;
import com.renaser.os.rag.application.ports.out.tiempo.ProgramarTareaPeriodicaPort.TareaProgramada;
import com.renaser.os.rag.application.services.ConversacionRenasiaService;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Una conversacion por voz viva: une lo que dice el modelo con lo que recibe la app, y cuenta el
 * tiempo (D-162). Nace en {@link ConversacionEnVivoService#iniciar} y muere al cerrarse.
 *
 * <p><b>Tres hilos la tocan</b>: el del proveedor (callbacks de {@link ConversacionEnVivoPort.Oyente},
 * de a uno), el del temporizador y el de la conexion de la app. Por eso el turno se guarda bajo
 * {@code synchronized} y el cierre es un {@link AtomicBoolean}: se cierra una sola vez, gane quien
 * gane.
 *
 * <p><b>Las herramientas corren con el actor de la sesion</b>, nunca con algo que diga el modelo,
 * igual que en el chat. Una escritura deja una propuesta con boton (D-153) y se le manda a la app
 * el evento {@code propuesta}; la voz nunca confirma nada.
 */
final class SesionDeVozEnVivo implements ConversacionEnVivo, ConversacionEnVivoPort.Oyente {

    private static final Logger log = LoggerFactory.getLogger(SesionDeVozEnVivo.class);

    static final String MENSAJE_SE_CORTO =
            "La conversacion por voz se corto. Puedes seguir escribiendo o volver a abrir el orbe.";
    static final String MENSAJE_DURACION_MAXIMA =
            "La conversacion por voz llego a su duracion maxima. Vuelve a abrir el orbe para seguir.";
    private static final String HERRAMIENTA_FALLO = "No pude hacerlo en este momento.";

    /** Lo que necesita, agrupado para no pasar diez parametros sueltos. */
    record Colaboradores(EjecutarHerramientaAgenteUseCase herramientas, ConsultarPropuestasDelTurnoUseCase propuestas,
                         TurnosDeVozEnVivo turnos, TiempoDeVozEnVivo tiempo, ProgramarTareaPeriodicaPort programador,
                         Clock clock, Duration intervaloDeCobro) {
    }

    private final UserId actorId;
    private final SalidaDeVozEnVivo salida;
    private final Colaboradores c;
    private final AtomicBoolean cerrada = new AtomicBoolean();
    private Instant inicio;
    private TurnoDeVoz turno = new TurnoDeVoz();
    private Instant cobradoHasta;
    private volatile SesionEnVivo sesion;
    private volatile TareaProgramada cobro;

    SesionDeVozEnVivo(UserId actorId, SalidaDeVozEnVivo salida, Colaboradores colaboradores) {
        this.actorId = actorId;
        this.salida = salida;
        this.c = colaboradores;
        this.inicio = colaboradores.clock().now();
        this.cobradoHasta = inicio;
    }

    /**
     * Ya abierta la sesion del proveedor: avisa a la app y empieza a contar. El reloj arranca aca y no
     * al construir: los segundos que tarda Gemini en aceptar la sesion no se le cobran a la persona.
     */
    void arrancar(SesionEnVivo abierta, Duration restante) {
        synchronized (this) {
            this.inicio = c.clock().now();
            this.cobradoHasta = inicio;
        }
        this.sesion = abierta;
        salida.evento(new EventoDeVozEnVivo.Listo(restante.toSeconds()));
        this.cobro = c.programador().cada(c.intervaloDeCobro(), this::cobrarTiempo);
    }

    // ---- app -> modelo ---------------------------------------------------------------------

    @Override
    public void recibirAudio(byte[] pcm16kHz) {
        SesionEnVivo abierta = sesion;
        if (!cerrada.get() && abierta != null) {
            abierta.enviarAudio(pcm16kHz);
        }
    }

    @Override
    public void terminar() {
        cerrar(null, MotivoDeCierre.NORMAL);
    }

    // ---- modelo -> app ---------------------------------------------------------------------

    @Override
    public void audio(byte[] pcm16kHz) {
        if (!cerrada.get()) {
            salida.audio(pcm16kHz);
        }
    }

    @Override
    public synchronized void oido(String texto) {
        turno.oir(texto, c.clock().now());
        emitir(new EventoDeVozEnVivo.Oido(texto));
    }

    @Override
    public synchronized void dicho(String texto) {
        turno.decir(texto, c.clock().now());
        emitir(new EventoDeVozEnVivo.Dicho(texto));
    }

    /**
     * Si el acompanante ya estaba respondiendo, lo que alcanzo a decir queda guardado como su
     * respuesta y lo que la persona diga ahora empieza un turno nuevo: asi el historial conserva el
     * orden real de la conversacion.
     */
    @Override
    public void interrumpido() {
        boolean yaRespondio;
        synchronized (this) {
            yaRespondio = turno.yaRespondio();
        }
        if (yaRespondio) {
            guardarTurno();
        }
        emitir(new EventoDeVozEnVivo.Interrumpido());
    }

    /**
     * Tras pedir una herramienta Gemini manda un {@code turnComplete} sin haber dicho nada, y otro al
     * terminar de hablar. El primero no cierra el turno: asi lo que dijo la persona, lo que respondio
     * y la propuesta quedan en un solo par de mensajes, y la app no deja de "pensar" antes de tiempo.
     */
    @Override
    public void turnoCompleto() {
        synchronized (this) {
            if (turno.esperandoRespuesta()) {
                return;
            }
        }
        guardarTurno();
        emitir(new EventoDeVozEnVivo.TurnoCompleto());
    }

    @Override
    public void pedidoDeHerramienta(String id, InvocacionHerramienta invocacion) {
        synchronized (this) {
            turno.marcarHerramienta();
        }
        Instant antes = c.clock().now();
        ResultadoHerramienta resultado = ejecutar(invocacion);
        SesionEnVivo abierta = sesion;
        if (abierta != null && !cerrada.get()) {
            abierta.responderHerramienta(id, invocacion.nombre(), resultado);
        }
        avisarPropuestasDesde(antes);
    }

    @Override
    public void cerrada(String motivo) {
        if (!cerrada.get()) {
            log.info("El proveedor cerro la conversacion por voz ({})", motivo);
        }
        cerrar(new EventoDeVozEnVivo.Error(MENSAJE_SE_CORTO), MotivoDeCierre.ERROR);
    }

    // ---- tiempo ----------------------------------------------------------------------------

    /**
     * Corta si la sesion llego a su maximo o si se acabo la cuota, y cobra lo hablado desde el ultimo
     * cobro.
     *
     * <p>El tope de la sesion se mira primero y sin Redis de por medio: si Redis falla, la cuota no se
     * puede contar, pero la sesion igual no pasa de su maximo.
     */
    void cobrarTiempo() {
        if (cerrada.get()) {
            return;
        }
        if (c.tiempo().cuota().sesionVencida(inicioDeLaSesion(), c.clock().now())) {
            cerrar(new EventoDeVozEnVivo.Error(MENSAJE_DURACION_MAXIMA), MotivoDeCierre.NORMAL);
            return;
        }
        Duration restante;
        try {
            restante = cobrarHastaAhora();
        } catch (RuntimeException e) {
            // Sin Redis no se puede contar; la conversacion sigue y el proximo cobro suma este tramo.
            log.warn("No se pudo cobrar el tiempo de la voz en vivo ({})", e.getClass().getSimpleName());
            return;
        }
        if (restante.isZero()) {
            cerrar(new EventoDeVozEnVivo.CuotaAgotada(), MotivoDeCierre.CUOTA_AGOTADA);
        }
    }

    private synchronized Instant inicioDeLaSesion() {
        return inicio;
    }

    /**
     * Solo segundos enteros: la fraccion queda para el cobro siguiente. El punto de corte avanza
     * recien cuando Redis confirmo la suma, asi un cobro que falla no pierde esos segundos.
     */
    private synchronized Duration cobrarHastaAhora() {
        Duration tramo = Duration.ofSeconds(Duration.between(cobradoHasta, c.clock().now()).toSeconds());
        Duration restante = c.tiempo().cobrar(actorId, tramo);
        cobradoHasta = cobradoHasta.plus(tramo);
        return restante;
    }

    // ---- internos --------------------------------------------------------------------------

    private ResultadoHerramienta ejecutar(InvocacionHerramienta invocacion) {
        try {
            return c.herramientas().ejecutar(actorId, invocacion);
        } catch (RuntimeException e) {
            log.warn("Fallo la herramienta {} en la voz en vivo ({})", invocacion.nombre(),
                    e.getClass().getSimpleName());
            return ResultadoHerramienta.fallo(HERRAMIENTA_FALLO);
        }
    }

    /** Mismo criterio que el chat: el resumen va tambien al mensaje guardado, para verlo en el historial. */
    private void avisarPropuestasDesde(Instant antes) {
        List<PropuestaCreada> nuevas;
        try {
            nuevas = c.propuestas().pendientesCreadasDesde(actorId, antes);
        } catch (RuntimeException e) {
            log.warn("No se pudieron recoger las propuestas de la voz en vivo ({})", e.getClass().getSimpleName());
            return;
        }
        for (PropuestaCreada propuesta : nuevas) {
            synchronized (this) {
                turno.anexar(ConversacionRenasiaService.ENCABEZADO_DE_PROPUESTA + propuesta.resumen());
            }
            emitir(new EventoDeVozEnVivo.Propuesta(propuesta.id(), propuesta.resumen(), propuesta.venceEn()));
        }
    }

    private void guardarTurno() {
        TurnoDeVoz terminado;
        synchronized (this) {
            terminado = turno;
            turno = new TurnoDeVoz();
        }
        if (terminado.vacio()) {
            return;
        }
        try {
            String apoyo = c.turnos().guardar(actorId, terminado);
            if (!apoyo.isBlank()) {
                emitir(new EventoDeVozEnVivo.Dicho(ConversacionRenasiaService.SEPARACION_DEL_APOYO + apoyo));
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar un turno de la voz en vivo ({}); la conversacion sigue",
                    e.getClass().getSimpleName());
        }
    }

    private void emitir(EventoDeVozEnVivo evento) {
        if (!cerrada.get()) {
            salida.evento(evento);
        }
    }

    /** Una sola vez: corta el cobro, cobra lo ultimo, guarda lo pendiente, cierra las dos puntas. */
    private void cerrar(EventoDeVozEnVivo aviso, MotivoDeCierre motivo) {
        if (!cerrada.compareAndSet(false, true)) {
            return;
        }
        TareaProgramada tarea = cobro;
        if (tarea != null) {
            tarea.cancelar();
        }
        cobrarAlCerrar();
        SesionEnVivo abierta = sesion;
        if (abierta != null) {
            abierta.cerrar();
        }
        guardarTurno();
        if (aviso != null) {
            salida.evento(aviso);
        }
        salida.cerrar(motivo);
    }

    /** Bajo el mismo candado que el cobro periodico: si los dos coinciden, ningun segundo se cobra dos veces. */
    private synchronized void cobrarAlCerrar() {
        try {
            // Al cerrar se cobra tambien la fraccion: redondeando hacia arriba nadie gana minutos gratis
            // abriendo y cerrando el orbe.
            Duration pendiente = Duration.between(cobradoHasta, c.clock().now());
            long segundos = pendiente.toSeconds() + (pendiente.toMillisPart() > 0 ? 1 : 0);
            c.tiempo().cobrar(actorId, Duration.ofSeconds(segundos));
            cobradoHasta = c.clock().now();
        } catch (RuntimeException e) {
            log.warn("No se pudo cobrar el ultimo tramo de la voz en vivo ({})", e.getClass().getSimpleName());
        }
    }
}
