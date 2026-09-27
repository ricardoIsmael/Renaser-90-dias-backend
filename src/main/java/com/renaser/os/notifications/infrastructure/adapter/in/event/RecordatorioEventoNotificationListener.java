package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.alarmalocal.ConsultarAlarmaLocalUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.evento.AvisoDeEvento;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Entrega los recordatorios de eventos del calendario: bandeja y push, tipo
 * {@code RECORDATORIO_EVENTO} (D-182, D-183).
 *
 * <p><b>Por que existe.</b> Hasta el 2026-09-26 nadie escuchaba {@link RecordatorioEventoDebidoEvent}.
 * {@code calendar} marcaba la fila de {@code recordatorios_evento} como enviada y publicaba el
 * evento al vacio: ningun recordatorio de evento llego nunca, incluida la alarma de 04:50 de la
 * Semana de Manifestacion (E-301).
 *
 * <p><b>Sin perdidas y sin duplicados.</b> {@code @ApplicationModuleListener} hace que Modulith
 * guarde la publicacion en el mismo commit en que {@code calendar} marca la fila enviada; si este
 * metodo falla, la publicacion queda incompleta y se reintenta (cada 5 minutos y al arrancar). El
 * reintento no duplica: la clave {@link AvisoDeEvento#claveDeduplicacion()} es una por fila de la
 * cola, y {@code notificaciones_origen_evento_uk} (V16) rechaza la segunda.
 *
 * <p><b>Corregido 2026-09-27 (E-360).</b> Lo de arriba no pasaba: el despacho marcaba las filas despues de
 * publicar, con un UPDATE que vaciaba el contexto de persistencia y se llevaba las publicaciones sin
 * escribirlas. Ninguna quedo nunca en {@code event_publication}, y un aviso que fallaba se perdia (17 de 29 el
 * 2026-09-27, cuando el pool se agoto). Ademas este metodo corria en un hilo nuevo por aviso, sin tope. Ahora
 * corre en el ejecutor de eventos ({@code renaser.eventos.concurrencia} hilos, {@code EjecucionAsincronaConfig})
 * y la publicacion se guarda de verdad ({@code AvisosDeEventoEnMasaIT}).
 *
 * <p><b>Preferencias y cuentas suspendidas</b> no se deciden aca: {@code NotificacionService.emitir}
 * no crea la fila si la persona apago {@code RECORDATORIO_EVENTO}, y no empuja al telefono de una
 * cuenta sin acceso vigente (E-38).
 *
 * <p><b>"Voy" y alarma local (D-189).</b> Quien respondio "Voy" desde la app del telefono tiene
 * una alarma local para esa ocurrencia, y un aviso del servidor encima seria doble. Pero la web no
 * programa nada: hasta el 2026-09-26 {@code calendar} apagaba los avisos al confirmar y quien
 * respondia desde el navegador se quedaba sin ninguno. Ahora el aviso se descarta aca, solo si la
 * persona dijo "Voy" Y tiene al menos un token de Android/iOS en este momento.
 */
@Component
class RecordatorioEventoNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioEventoNotificationListener.class);

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ConsultarAlarmaLocalUseCase alarmaLocal;
    private final Clock clock;

    RecordatorioEventoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                           ConsultarAlarmaLocalUseCase alarmaLocal, Clock clock) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.alarmaLocal = alarmaLocal;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(RecordatorioEventoDebidoEvent event) {
        AvisoDeEvento aviso = new AvisoDeEvento(event.recordatorioId(), event.eventoId(), event.tituloEvento(),
                event.inicioOcurrencia(), ZoneId.of(event.zonaHoraria()), event.esAnuncio());
        Instant ahora = clock.now();
        if (aviso.yaNoSirve(ahora)) {
            log.info("[notifications.RecordatorioEventoNotificationListener] recordatorio {} descartado: "
                    + "la ocurrencia del {} ya empezo", event.recordatorioId(), event.inicioOcurrencia());
            return;
        }
        if (event.confirmoAsistencia() && alarmaLocal.tieneAlarmaLocal(event.destinatarioId())) {
            log.info("[notifications.RecordatorioEventoNotificationListener] recordatorio {} no se envia: "
                    + "{} dijo \"Voy\" y su telefono tiene la alarma local", event.recordatorioId(),
                    event.destinatarioId());
            return;
        }
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.destinatarioId(),
                TipoNotificacion.RECORDATORIO_EVENTO, aviso.titulo(), aviso.cuerpo(ahora), aviso.rutaApp(),
                aviso.claveDeduplicacion()));
    }
}
