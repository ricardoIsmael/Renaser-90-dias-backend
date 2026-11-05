package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.CuentaDeGoogleRevocadaEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Avisa a Administración y Alquimista que la cuenta de Google de Renaser necesita volver a conectarse (spec 040, FR-009).
 *
 * <p><b>A quién:</b> ADMIN y ALCHEMIST activos, los mismos que pueden conectarla. <b>Con qué tipo:</b> {@code ANUNCIO_SISTEMA}, para no sumar un
 * valor al enum de Postgres por un aviso de operación. <b>Cuántos:</b> el {@code origenEventoId} es el del día
 * ({@link CuentaDeGoogleRevocadaEvent#avisoId()}): el índice único de {@code notificaciones} descarta el segundo del mismo día (C-7).
 *
 * <p>No lleva ninguna ruta: tocarlo abre la app, y la pantalla «Cuenta de Google» está en Yo → Administración → Más opciones.
 */
@Component
class CuentaDeGoogleRevocadaNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CuentaDeGoogleRevocadaNotificationListener.class);

    private static final Set<UserRole> QUIENES_LA_CONECTAN = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);
    static final String TITULO = "Hay que volver a conectar Google";
    static final String CUERPO = "La cuenta de Google de Renaser necesita volver a conectarse para crear los enlaces de Meet.";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;

    CuentaDeGoogleRevocadaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                               ParticipacionProgramaFinder participacionFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
    }

    @ApplicationModuleListener
    void on(CuentaDeGoogleRevocadaEvent evento) {
        List<UserId> destinatarios = participacionFinder.usuariosActivosConRol(QUIENES_LA_CONECTAN);
        if (destinatarios.isEmpty()) {
            log.warn("[notifications.CuentaDeGoogleRevocada] no hay ADMIN ni ALCHEMIST activos a quienes avisar");
            return;
        }
        for (UserId destinatario : destinatarios) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.ANUNCIO_SISTEMA,
                    TITULO, CUERPO, null, evento.avisoId()));
        }
    }
}
