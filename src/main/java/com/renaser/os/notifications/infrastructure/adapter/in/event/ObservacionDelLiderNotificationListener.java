package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * «Tu líder te dejó una observación»: le avisa al mentor, en su teléfono, que el líder de mentores le dejó un reconocimiento, una sugerencia o una
 * alerta (spec 056, FR-025). Bandeja y push; {@code EmitirNotificacionUseCase} respeta las preferencias del mentor.
 *
 * <p>El tipo es {@link TipoNotificacion#REVISION_DEL_LIDER}, el de todo lo que el líder hace sobre el mentor y su grupo: no se agregó uno nuevo porque eso
 * pide cambiar el tipo de Postgres ({@code tipo_notificacion}) y esta spec no lleva migraciones. Si el mentor silencia ese tipo, silencia también estos
 * avisos; el texto de la observación igual queda en su chat con el líder.
 *
 * <p><b>El cuerpo no repite el texto de la observación</b>: va por push y puede verse con la pantalla bloqueada. El push del propio mensaje del chat lo
 * manda {@code MensajeDeChatNotificationListener}; este es el aviso de la observación, que además se ve en la campana. Tocarlo abre el chat de dos donde
 * está el texto; la app revalida allí que el mentor siga siendo participante.
 *
 * <p>La deduplicación viaja en {@code claveDeduplicacion} (una por observación): la redelivery del outbox no crea otro aviso ni otro push.
 */
@Component
class ObservacionDelLiderNotificationListener {

    static final String CUERPO = "El texto está en tu chat con tu líder.";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    ObservacionDelLiderNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(ObservacionDelLiderEvent event) {
        // SIN-SUPERAR: la clave es la de UNA observación concreta (un hecho único): cada observación es un aviso distinto y no hay un aviso anterior
        // que quede sin leer con una cifra vieja.
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(UserId.of(event.mentorId()), TipoNotificacion.REVISION_DEL_LIDER,
                titulo(event.tipo()), CUERPO, event.rutaApp(), event.claveDeduplicacion()));
    }

    /** Qué le dejó el líder, en tipo oración y con tuteo. Un tipo que no se reconoce (uno nuevo mañana) se avisa con el texto neutro, nunca falla. */
    private static String titulo(String tipo) {
        return switch (tipo) {
            case "RECONOCIMIENTO" -> "Tu líder te dejó un reconocimiento";
            case "SUGERENCIA" -> "Tu líder te dejó una sugerencia";
            case "ALERTA" -> "Tu líder te dejó una alerta";
            default -> "Tu líder te dejó una observación";
        };
    }
}
