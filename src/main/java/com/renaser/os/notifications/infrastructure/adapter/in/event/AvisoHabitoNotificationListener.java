package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Convierte los dos avisos automaticos de habito de `habits` en una fila real de la bandeja
 * (pedido del dueno, 2026-09-05). Reparto de siempre: `habits` decide QUE avisar y CUANDO,
 * este modulo decide COMO se entrega.
 *
 * <p><b>Notificacion en la app y Web Push</b>. La fila en {@code notificaciones} es visible desde
 * la bandeja; el push sale por {@code PushPort}. {@code WebPushAdapter} entrega las suscripciones
 * de navegador con VAPID y deja intactos los tokens nativos para su adaptador correspondiente.
 * Un fallo externo nunca revierte la fila de la bandeja: {@code NotificacionService.emitir} ya
 * trata el push como best-effort.
 *
 * <p><b>Tipo {@code RECORDATORIO_HABITO}</b>: existe en el baseline desde V1 y es exactamente
 * este caso, asi que no hizo falta ningun valor de enum nuevo ni ninguna migracion. Como
 * consecuencia util, el aprendiz puede apagar los dos avisos desde sus preferencias de
 * notificaciones sin ninguna pantalla nueva — {@code NotificacionService.emitir} respeta
 * {@code PreferenciaNotificacion} antes de crear la fila.
 *
 * <p><b>Los dos avisos comparten tipo pero no clave.</b> {@code claveEvento} llega ya distinta
 * por tipo de aviso desde `habits` (ver {@code TipoAvisoHabito.claveIdempotencia}), porque el
 * indice unico de deduplicacion es {@code (usuario_id, tipo, origen_evento_id)} (C-7/V16): con
 * el {@code registroId} pelado, el aviso de vencimiento se habria descartado como duplicado del
 * de inicio. Esa misma clave es la que hace que el barrido de cada 5 minutos pueda recalcular
 * los avisos sin mandar nada dos veces.
 *
 * <p>El {@code switch} sobre el nombre del tipo llega como String, no como enum, a proposito:
 * {@code TipoAvisoHabito} vive en un paquete interno de `habits` y no puede cruzar su
 * {@code @NamedInterface} (mismo criterio que {@code HabitoDelDiaResumen.estado}).
 */
@Component
class AvisoHabitoNotificationListener {

    private static final String AVISO_INICIO = "INICIO";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    AvisoHabitoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(AvisoHabitoDebidoEvent event) {
        boolean esInicio = AVISO_INICIO.equals(event.tipoAviso());
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.RECORDATORIO_HABITO, esInicio ? "Tu habito esta por empezar" : "Se te vence un habito",
                cuerpoDe(event, esInicio), null, event.claveEvento()));
    }

    /**
     * Los puntos en juego van en el texto por pedido explicito del dueno ("al mencionar un
     * habito, decir cuantos puntos se ganan o se pierden"). El numero lo calcula `habits` con la
     * escala real de D-97 y viaja en el evento: este modulo no lo recalcula ni lo redondea.
     */
    private static String cuerpoDe(AvisoHabitoDebidoEvent event, boolean esInicio) {
        String cuando = esInicio
                ? "empieza en " + event.minutosQueFaltan() + " min"
                : "vence en " + event.minutosQueFaltan() + " min";
        return event.tituloHabito() + " " + cuando + ". En juego: " + event.puntosEnJuego() + " puntos.";
    }
}
