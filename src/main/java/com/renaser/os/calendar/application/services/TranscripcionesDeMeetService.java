package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase.EstadoDeLaFecha;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.ReunionesDeMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.TranscripcionesDeMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.TranscripcionesDeMeetPort.ParaBorrar;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.transcripcion.DiagnosticoDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.EstadoDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.PendienteDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.TranscripcionDeSesion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Spec 047: lee de Google la transcripción de cada sesión por Meet y la guarda cifrada, sin mostrarla; la borra a tiempo; y deja que ADMIN y
 * ALCHEMIST la saquen con un motivo, con constancia.
 *
 * <p>El registro de la aplicación NUNCA lleva el texto, el enlace de la grabación ni lo que respondió Google: solo cantidades y estados (FR-010).
 */
@Service
class TranscripcionesDeMeetService implements TranscripcionesDeMeetUseCase {

    private static final Logger log = LoggerFactory.getLogger(TranscripcionesDeMeetService.class);
    /** La constancia de quién sacó una transcripción se guarda 12 meses, como la auditoría de la 040. */
    static final Duration RETENCION_DE_LAS_LECTURAS = Duration.ofDays(365);
    static final int LARGO_MINIMO_DEL_MOTIVO = 10;
    static final int LARGO_MAXIMO_DEL_MOTIVO = 500;
    private static final int BORRADOS_POR_VUELTA = 500;

    private final TranscripcionesDeMeetPort transcripciones;
    private final ReunionesDeMeetPort reuniones;
    private final LoadCuentaGooglePort cuentaPort;
    private final CifradoDeSecretosPort cifrado;
    private final LoadEventoPort eventos;
    private final AccesoEventoService acceso;
    private final AuditoriaDeGoogle auditoria;
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration retencion;
    private final int porVuelta;

    TranscripcionesDeMeetService(TranscripcionesDeMeetPort transcripciones, ReunionesDeMeetPort reuniones, LoadCuentaGooglePort cuentaPort,
                                 CifradoDeSecretosPort cifrado, LoadEventoPort eventos, AccesoEventoService acceso, AuditoriaDeGoogle auditoria,
                                 Clock clock, IdGenerator ids,
                                 @Value("${renaser.calendar.meet.transcripcion.retencion-dias:180}") int retencionEnDias,
                                 @Value("${renaser.calendar.meet.transcripcion.por-vuelta:5}") int porVuelta) {
        this.transcripciones = transcripciones;
        this.reuniones = reuniones;
        this.cuentaPort = cuentaPort;
        this.cifrado = cifrado;
        this.eventos = eventos;
        this.acceso = acceso;
        this.auditoria = auditoria;
        this.clock = clock;
        this.ids = ids;
        this.retencion = Duration.ofDays(Math.max(1, retencionEnDias));
        this.porVuelta = Math.max(1, porVuelta);
    }

    @Override
    public int buscarPendientes(Instant ahora) {
        if (!cifrado.estaConfigurado()) {
            log.warn("[calendar.TranscripcionesDeMeet] falta la clave de cifrado: no se busca ninguna transcripción (FR-010)");
            return 0;
        }
        Optional<CuentaDeGoogle> cuenta = cuentaPort.cargar();
        if (cuenta.isEmpty() || !cuenta.get().activa()) {
            return 0;
        }
        boolean puedeLeer = cuenta.get().tienePermiso(CuentaDeGoogle.PERMISO_LEER_REUNIONES);
        List<PendienteDeTranscripcion> pendientes = transcripciones.pendientes(ahora, porVuelta);
        int revisadas = 0;
        for (PendienteDeTranscripcion p : pendientes) {
            TranscripcionDeSesion t = p.actual() != null ? p.actual() : TranscripcionDeSesion.nueva(ids.newId(), p.salaId(), ahora);
            try {
                if (Boolean.FALSE.equals(p.transcripcionConfirmada())) {
                    t.sinTranscripcionPorLaCuenta(ahora);
                } else if (!puedeLeer) {
                    t.sinPermiso(ahora);
                } else {
                    LecturaDeReunion lectura = reuniones.leer(p.enlace(), p.desde(), p.hasta());
                    t.registrarLectura(lectura, p.fin(), ahora, retencion);
                }
            } catch (GoogleNoRespondeException e) {
                t.googleNoRespondio(ahora);
            } catch (GoogleRechazoException e) {
                if (e.codigoHttp() == 403) {
                    t.sinPermiso(ahora);
                } else {
                    t.googleNoRespondio(ahora);
                }
            } catch (CuentaGoogleRevocadaException | CuentaGoogleNoConectadaException e) {
                // La cuenta ya no vale: no se toca la fila y se corta la vuelta. La 040 avisa a Administración que hay que volver a conectar.
                log.warn("[calendar.TranscripcionesDeMeet] la cuenta de Google no responde con permiso: se corta la vuelta");
                break;
            }
            transcripciones.guardar(t);
            registrarResultado(t, p.eventoId());
            revisadas++;
        }
        if (revisadas > 0) {
            log.info("[calendar.TranscripcionesDeMeet] {} sala(s) revisada(s)", revisadas);
        }
        return revisadas;
    }

    private void registrarResultado(TranscripcionDeSesion t, EventoId eventoId) {
        switch (t.estado()) {
            case LISTA -> auditoria.registrar(AccionDeAuditoriaGoogle.TRANSCRIPCION_GUARDADA, null, eventoId.value(), true, null,
                    auditoria.nuevaCorrelacion());
            case SIN_REUNION, SIN_TRANSCRIPCION -> auditoria.registrar(AccionDeAuditoriaGoogle.TRANSCRIPCION_SIN_DATOS, null, eventoId.value(),
                    true, null, auditoria.nuevaCorrelacion());
            default -> {
                // ESPERANDO y SIN_PERMISO no dejan constancia en cada vuelta: se repiten cada pocos minutos.
            }
        }
    }

    @Override
    public int aplicarRetencion(Instant ahora) {
        List<ParaBorrar> lista = transcripciones.paraBorrar(ahora, BORRADOS_POR_VUELTA);
        for (ParaBorrar b : lista) {
            transcripciones.borrar(b.transcripcionId(), ahora);
            auditoria.registrar(AccionDeAuditoriaGoogle.TRANSCRIPCION_BORRADA, null, b.eventoId().value(), true, null,
                    auditoria.nuevaCorrelacion());
        }
        transcripciones.purgarLecturasAnterioresA(ahora.minus(RETENCION_DE_LAS_LECTURAS));
        if (!lista.isEmpty()) {
            log.info("[calendar.TranscripcionesDeMeet] {} transcripción(es) borrada(s) por retención", lista.size());
        }
        return lista.size();
    }

    @Override
    public List<EstadoDeLaFecha> estados(UserId actor, EventoId eventoId) {
        acceso.requireAdministraElCalendario(actor);
        exigirEvento(eventoId);
        return transcripciones.estadosDelEvento(eventoId).stream()
                .map(e -> new EstadoDeLaFecha(e.fecha(), e.estado(), e.motivo(), e.obtenidaEn(), e.borrarDespuesDe(), e.grabacion(),
                        e.diagnostico()))
                .toList();
    }

    @Override
    public DiagnosticoDeReunion diagnosticar(UserId actor, EventoId eventoId, LocalDate fecha) {
        acceso.requireAdministraElCalendario(actor);
        exigirEvento(eventoId);
        PendienteDeTranscripcion sala = transcripciones.salaDeLaFecha(eventoId, fecha)
                .orElseThrow(() -> new NoSuchElementException("Esa fecha no tiene una sala de Meet creada por Renaser."));
        Optional<CuentaDeGoogle> cuenta = cuentaPort.cargar();
        if (cuenta.isEmpty() || !cuenta.get().activa() || !cuenta.get().tienePermiso(CuentaDeGoogle.PERMISO_LEER_REUNIONES)) {
            throw new IllegalStateException("La cuenta de Google no está conectada con el permiso de leer las reuniones.");
        }
        LecturaDeReunion lectura;
        try {
            lectura = reuniones.leer(sala.enlace(), sala.desde(), sala.hasta());
        } catch (GoogleNoRespondeException | GoogleRechazoException | CuentaGoogleRevocadaException | CuentaGoogleNoConectadaException e) {
            throw new IllegalStateException("Google no dejó leer esa reunión ahora. Intenta de nuevo en unos minutos.");
        }
        DiagnosticoDeReunion diagnostico = lectura.diagnostico();
        if (diagnostico == null) {
            // Esta vez Google sí dejó texto: no hay nada que explicar; se avisa sin tocar la fila.
            throw new IllegalStateException("Esa sesión sí tiene transcripción en Google: no hay nada que diagnosticar.");
        }
        transcripciones.deLaFecha(eventoId, fecha).ifPresent(t -> {
            if (t.registrarDiagnostico(diagnostico, clock.now())) {
                transcripciones.guardar(t);
            }
        });
        log.info("[calendar.TranscripcionesDeMeet] diagnóstico a pedido: {} conexión(es), motivo {}", diagnostico.conferencias().size(),
                diagnostico.motivoProbable());
        return diagnostico;
    }

    @Override
    public TranscripcionDescargada descargar(UserId actor, EventoId eventoId, LocalDate fecha, String motivo) {
        acceso.requireAdministraElCalendario(actor);
        String limpio = motivoValido(motivo, "Escribe por qué necesitas la transcripción (de 10 a 500 caracteres).");
        exigirEvento(eventoId);
        TranscripcionDeSesion t = transcripciones.deLaFecha(eventoId, fecha)
                .filter(x -> x.estado() == EstadoDeTranscripcion.LISTA && x.texto() != null)
                .orElseThrow(() -> new NoSuchElementException("No hay una transcripción guardada de esa fecha."));
        Instant ahora = clock.now();
        transcripciones.registrarAcceso(actor, eventoId, fecha, "LECTURA", limpio, ahora);
        auditoria.registrar(AccionDeAuditoriaGoogle.TRANSCRIPCION_LEIDA, actor, eventoId.value(), true, null, auditoria.nuevaCorrelacion());
        return new TranscripcionDescargada(fecha, t.texto(), t.enlaceDeLaGrabacion(), t.recortada());
    }

    @Override
    public void borrarAPedido(UserId actor, EventoId eventoId, LocalDate fecha, String motivo) {
        acceso.requireAdministraElCalendario(actor);
        String limpio = motivoValido(motivo, "Escribe por qué se borra la transcripción (de 10 a 500 caracteres).");
        exigirEvento(eventoId);
        TranscripcionDeSesion t = transcripciones.deLaFecha(eventoId, fecha)
                .filter(x -> x.estado() == EstadoDeTranscripcion.LISTA)
                .orElseThrow(() -> new NoSuchElementException("No hay una transcripción guardada de esa fecha."));
        Instant ahora = clock.now();
        transcripciones.borrar(t.id(), ahora);
        transcripciones.registrarAcceso(actor, eventoId, fecha, "BORRADO", limpio, ahora);
        auditoria.registrar(AccionDeAuditoriaGoogle.TRANSCRIPCION_BORRADA, actor, eventoId.value(), true, null, auditoria.nuevaCorrelacion());
    }

    private static String motivoValido(String motivo, String siNo) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() < LARGO_MINIMO_DEL_MOTIVO || limpio.length() > LARGO_MAXIMO_DEL_MOTIVO) {
            throw new IllegalArgumentException(siNo);
        }
        return limpio;
    }

    private void exigirEvento(EventoId eventoId) {
        if (eventos.byId(eventoId).isEmpty()) {
            throw new NoSuchElementException("Evento no encontrado: " + eventoId);
        }
    }
}
