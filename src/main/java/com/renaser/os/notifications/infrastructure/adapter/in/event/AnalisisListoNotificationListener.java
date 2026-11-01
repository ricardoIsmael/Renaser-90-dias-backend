package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.rag.api.AnalisisListoEvent;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * «Tu análisis está listo»: avisa a la persona que el análisis 80/20 de su semana o de su mes ya se puede leer (spec 031, FR-024). Bandeja y
 * push, tipo {@code ANALISIS_80_20}, propio para que se pueda apagar desde Yo → Notificaciones sin apagar el aviso del semáforo;
 * {@code EmitirNotificacionUseCase} respeta las preferencias de la persona.
 *
 * <p><b>El texto es fijo y no lleva nada del análisis</b>: ni cifras, ni frases, ni lo que la persona escribió. El contenido es dato
 * personal y se lee en la app, donde se revalidan los permisos. Tocar el aviso abre la hoja «Tu 80/20» de Plan en ese periodo. La
 * deduplicación viaja en {@code claveDeduplicacion} (el id del análisis): un reintento del outbox no crea otro aviso ni otro push.
 */
@Component
class AnalisisListoNotificationListener {

    static final String TITULO_DE_LA_SEMANA = "Tu análisis de la semana está listo";
    static final String TITULO_DEL_MES = "Tu análisis del mes está listo";
    static final String CUERPO = "Entra a Plan y míralo.";
    static final String RUTA = "/plan/80-20";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    AnalisisListoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(AnalisisListoEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(UserId.of(event.personaId()), TipoNotificacion.ANALISIS_80_20,
                "MES".equals(event.periodo()) ? TITULO_DEL_MES : TITULO_DE_LA_SEMANA, CUERPO,
                RUTA + "?periodo=" + event.periodo() + "&inicio=" + event.inicio(), event.claveDeDeduplicacion()));
    }
}
