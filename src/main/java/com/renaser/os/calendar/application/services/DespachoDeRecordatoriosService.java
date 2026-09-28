package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code despachar()} del repo viejo: toma los recordatorios vencidos y PUBLICA
 * {@link RecordatorioEventoDebidoEvent} por cada uno; {@code notifications} decide el canal.
 *
 * <p><b>Que significa "enviado" (D-182).</b> {@code enviado_en} quiere decir "entregado al outbox", no
 * "llego al telefono". El evento se publica DENTRO de la transaccion del lote: Spring Modulith guarda la
 * publicacion en {@code event_publication} en el mismo commit que el {@code UPDATE} de {@code enviado_en}.
 * Si despues {@code notifications} no logra crear la notificacion, la publicacion queda incompleta y
 * {@code EventPublicationMaintenanceScheduler} la reintenta (cada 5 minutos y al arrancar); el reintento no
 * duplica porque la notificacion se deduplica por el {@code id} de la fila. {@code AvisosDeEventoEnMasaIT}
 * lo prueba con una falla de verdad de la base.
 *
 * <p><b>Corregido 2026-09-27 (E-360): esa garantia no se cumplia.</b> Marcar la fila enviada es un UPDATE con
 * {@code clearAutomatically}, que vacia el contexto de persistencia y cancela lo pendiente de escribir: las
 * publicaciones del outbox, que Modulith guarda con un {@code persist}. Ningun recordatorio de evento quedo
 * nunca en {@code event_publication}; el que fallaba al entregarse se perdia con la fila ya marcada (los 17 de
 * los 29 del 2026-09-27). Ahora se marca antes de publicar, y el adaptador hace flush antes de vaciar.
 *
 * <p><b>De a lotes, cada uno en su transaccion (E-360, 2026-09-27).</b> Antes era una sola transaccion
 * de hasta 500 filas por minuto: con mil personas avisadas para la misma hora, la mitad salia un minuto
 * tarde, y el evento se volvia a leer una vez por fila. Ahora cada lote de {@link #TAMANO_LOTE} va en su
 * propia transaccion (regla 02 §4: un barrido masivo pagina y no va en un {@code @Transactional} unico),
 * la pasada sigue hasta vaciar la cola, y cada evento se lee una vez por lote. Quien acota cuantos avisos
 * se ENTREGAN a la vez es el ejecutor de los listeners ({@code renaser.eventos.concurrencia}), no esto.
 *
 * <p><b>El anuncio dice cuando es el evento (E-361, 2026-09-27).</b> La fila del anuncio tiene clave fija
 * ({@code enviar_en = inicio_ocurrencia = creado_en} del evento, para que dos pasadas no lo dupliquen), y
 * esa clave viajaba como si fuera el inicio: «Nuevo evento: Clase desde la app. Es el sabado 26 de setiembre
 * a las 20:02», la hora en que se creo, cuando la clase era el domingo a las 19:00. Ahora el anuncio lleva
 * el inicio real: el del evento, o el de su proxima ocurrencia si es una serie.
 */
@Service
public class DespachoDeRecordatoriosService implements DespacharRecordatoriosUseCase {

    private static final Logger log = LoggerFactory.getLogger(DespachoDeRecordatoriosService.class);

    /** Filas por transaccion: la cola se toma con {@code FOR UPDATE SKIP LOCKED}, asi que un lote chico
     * tambien acorta cuanto tiempo quedan bloqueadas para otra instancia. */
    static final int TAMANO_LOTE = 200;
    /** Tope de una pasada (10.000 avisos); lo que quede sale en la del minuto siguiente. */
    static final int MAXIMO_LOTES_POR_PASADA = 50;
    /** Hasta donde se busca la proxima ocurrencia de una serie para decirla en el anuncio. */
    private static final Duration HORIZONTE_DEL_ANUNCIO = Duration.ofDays(ListarEventosParaVisorUseCase.RANGO_MAXIMO_DIAS);

    private final LoadRecordatorioPort loadRecordatorioPort;
    private final SaveRecordatorioPort saveRecordatorioPort;
    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final LoadConfirmacionPort loadConfirmacionPort;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaccionDelLote;

    public DespachoDeRecordatoriosService(LoadRecordatorioPort loadRecordatorioPort,
                                          SaveRecordatorioPort saveRecordatorioPort, LoadEventoPort loadEventoPort,
                                          LoadExcepcionPort loadExcepcionPort,
                                          LoadConfirmacionPort loadConfirmacionPort, ApplicationEventPublisher events,
                                          PlatformTransactionManager transactionManager) {
        this.loadRecordatorioPort = loadRecordatorioPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.loadConfirmacionPort = loadConfirmacionPort;
        this.events = events;
        this.transaccionDelLote = new TransactionTemplate(transactionManager);
    }

    /** Sin {@code @Transactional} a proposito: cada lote abre y cierra la suya. */
    @Override
    public int despachar(Instant ahora) {
        int despachados = 0;
        for (int lote = 0; lote < MAXIMO_LOTES_POR_PASADA; lote++) {
            ResultadoDelLote resultado = transaccionDelLote.execute(status -> despacharUnLote(ahora));
            despachados += resultado.despachados();
            if (resultado.tomados() < TAMANO_LOTE) {
                break;
            }
        }
        return despachados;
    }

    private ResultadoDelLote despacharUnLote(Instant ahora) {
        List<RecordatorioEvento> pendientes = loadRecordatorioPort.vencidosPendientes(ahora, TAMANO_LOTE);
        if (pendientes.isEmpty()) {
            return new ResultadoDelLote(0, 0);
        }
        Lote lote = new Lote(AsistenciasConfirmadasDelLote.de(pendientes, loadConfirmacionPort), ahora);
        pendientes.forEach(lote::clasificar);
        marcar(lote, ahora);
        lote.avisos.forEach(events::publishEvent);
        log.debug("[DespachoDeRecordatoriosService] {} despachado(s) y {} cancelado(s) de {} pendiente(s)",
                lote.avisos.size(), lote.cancelados.size(), pendientes.size());
        return new ResultadoDelLote(pendientes.size(), lote.avisos.size());
    }

    /**
     * Se marca ANTES de publicar (E-360). Los dos UPDATE vacian el contexto de persistencia
     * ({@code clearAutomatically}), y Spring Modulith guarda cada publicacion con un {@code persist} que se
     * escribe recien en el flush: publicar primero y marcar despues se llevaba las publicaciones sin escribirlas.
     * El adaptador ya hace flush antes de vaciar; este orden no depende de eso.
     */
    private void marcar(Lote lote, Instant ahora) {
        if (!lote.cancelados.isEmpty()) {
            saveRecordatorioPort.cancelarPorIds(lote.cancelados, RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);
        }
        if (!lote.avisos.isEmpty()) {
            saveRecordatorioPort.marcarEnviados(lote.avisos.stream().map(RecordatorioEventoDebidoEvent::recordatorioId)
                    .toList(), ahora);
        }
    }

    /**
     * El inicio que dice el anuncio: el del evento, o el de la proxima ocurrencia (con su excepcion, si la
     * movieron) cuando es una serie que ya habia empezado. Si en el horizonte no queda ninguna, el del evento.
     */
    private Instant inicioQueSeAnuncia(Evento evento, Instant ahora) {
        if (!evento.esRecurrente()) {
            return evento.iniciaEn();
        }
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(),
                evento.timezone(), evento.recurrencia(), ahora, ahora.plus(HORIZONTE_DEL_ANUNCIO),
                loadExcepcionPort.porEvento(evento.id()));
        return ocurrencias.stream()
                .map(Ocurrencia::iniciaEn)
                .filter(inicio -> !inicio.isBefore(ahora))
                .min(Comparator.naturalOrder())
                .orElse(evento.iniciaEn());
    }

    /** Filas tomadas de la cola y cuantas se publicaron: si se tomo un lote entero, puede quedar mas. */
    private record ResultadoDelLote(int tomados, int despachados) {
    }

    /** Lo que se va juntando mientras se recorre un lote: cada evento se lee una sola vez. */
    private final class Lote {

        private final AsistenciasConfirmadasDelLote asistencias;
        private final Instant ahora;
        private final Map<EventoId, Optional<Evento>> eventos = new HashMap<>();
        private final Map<EventoId, Instant> inicioAnunciado = new HashMap<>();
        private final List<RecordatorioEventoDebidoEvent> avisos = new ArrayList<>();
        private final List<Long> cancelados = new ArrayList<>();

        private Lote(AsistenciasConfirmadasDelLote asistencias, Instant ahora) {
            this.asistencias = asistencias;
            this.ahora = ahora;
        }

        /** Arma el aviso o anota la cancelacion; todavia no publica ni escribe nada. */
        private void clasificar(RecordatorioEvento recordatorio) {
            Optional<Evento> evento = eventos.computeIfAbsent(recordatorio.eventoId(), loadEventoPort::byId);
            if (evento.isEmpty()) {
                // FK ON DELETE CASCADE deberia impedir esto; guard defensivo.
                return;
            }
            if (evento.get().estado() == EstadoEvento.CANCELADO) {
                cancelados.add(recordatorio.id());
                return;
            }
            avisos.add(aviso(recordatorio, evento.get()));
        }

        private RecordatorioEventoDebidoEvent aviso(RecordatorioEvento recordatorio, Evento evento) {
            boolean esAnuncio = recordatorio.esAnuncio(evento.creadoEn());
            Instant inicio = esAnuncio
                    ? inicioAnunciado.computeIfAbsent(evento.id(), id -> inicioQueSeAnuncia(evento, ahora))
                    : recordatorio.inicioOcurrencia();
            boolean asistenciaConfirmada = !esAnuncio && asistencias.incluye(recordatorio);
            return new RecordatorioEventoDebidoEvent(recordatorio.id(), evento.id().value(), recordatorio.usuarioId(),
                    inicio, evento.titulo(), esAnuncio, asistenciaConfirmada, evento.timezone().getId(), ahora);
        }
    }
}
