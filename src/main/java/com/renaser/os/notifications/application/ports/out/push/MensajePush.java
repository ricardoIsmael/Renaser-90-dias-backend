package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.notificacion.EtiquetaDelAviso;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;

import java.time.Instant;
import java.util.Optional;

/**
 * Lo que sale en un push, sin el destino.
 *
 * <p>Existe desde D-188 (E-9 de la retroalimentación del 26/09): el transporte de Expo necesita
 * saber el {@link TipoNotificacion} para elegir el canal de Android, y agregarlo como quinto
 * parámetro suelto a {@link PushPort#enviar} y {@link TransportePush#entregar} dejaba dos firmas
 * con cuatro {@code String}/enum intercambiables en fila.
 *
 * @param tipo    de qué es el aviso. Cada transporte decide qué hace con él (hoy solo Expo lo usa,
 *                para el {@code channelId}); puede ser {@code null} y entonces nadie elige canal.
 * @param rutaApp destino lógico dentro de la app al tocarlo. Puede ser {@code null}.
 * @param etiquetaPropia la etiqueta que el emisor decidió expresamente, o {@code null} para derivarla como
 *                siempre ({@link EtiquetaDelAviso}). Existe desde la spec 007 (FR-014): el aviso de un hábito
 *                se reemplaza con la alarma local del mismo hábito y momento ({@code habito-{id}-inicio}), y
 *                esa etiqueta no se puede deducir de la ruta ({@code /habitos/{id}?dimension=…} no dice si es
 *                el aviso de inicio o el de vencimiento). Viaja por aquí y no por {@code
 *                EmitirNotificacionCommand}, que tiene veintitantos llamadores y un único constructor.
 * @param venceEn el instante después del cual el aviso ya no tiene sentido, o {@code null} si no caduca. Existe
 *                desde E-645 (issue #22): el servidor ya no manda un aviso cuyo momento pasó
 *                ({@code AvisoDeHabito.yaNoSirve}, {@code AvisoDeEvento.yaNoSirve}), pero el proveedor del push
 *                (FCM, hasta 4 semanas) lo guarda y lo entrega cuando el teléfono recupera la red o sale del
 *                reposo: «te toca a las 07:00» llegaba pasadas las 8. Con este instante el transporte le dice al
 *                proveedor cuándo descartarlo. Solo lo ponen los avisos que son «a una hora» (hábitos y
 *                recordatorios de evento); chat, logros y avisos manuales van con {@code null} y no cambian.
 *                Viaja por aquí, igual que la etiqueta, y no por {@code EmitirNotificacionCommand}.
 */
public record MensajePush(TipoNotificacion tipo, String titulo, String cuerpo, String rutaApp,
                          String etiquetaPropia, Instant venceEn) {

    /** Un aviso sin etiqueta propia: la de chat se sigue derivando de la ruta (D-221) y el resto no lleva. */
    public MensajePush(TipoNotificacion tipo, String titulo, String cuerpo, String rutaApp) {
        this(tipo, titulo, cuerpo, rutaApp, null, null);
    }

    /** Un aviso que no caduca (todos los de antes de E-645), con o sin etiqueta propia. */
    public MensajePush(TipoNotificacion tipo, String titulo, String cuerpo, String rutaApp, String etiquetaPropia) {
        this(tipo, titulo, cuerpo, rutaApp, etiquetaPropia, null);
    }

    /**
     * Con qué avisos anteriores se reemplaza; vacío = ninguno. La etiqueta propia manda; si no hay, la de
     * los avisos de un mismo chat (D-221).
     */
    public Optional<String> etiqueta() {
        if (etiquetaPropia != null && !etiquetaPropia.isBlank()) {
            return Optional.of(etiquetaPropia);
        }
        return EtiquetaDelAviso.de(tipo, rutaApp);
    }
}
