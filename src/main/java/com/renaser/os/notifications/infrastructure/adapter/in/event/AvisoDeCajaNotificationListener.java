package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Los avisos de la Caja Renaser en la bandeja y el push (D-219, spec §5), tipo {@code HITO_PROGRAMA}.
 *
 * <p>El {@code origen_evento_id} es el {@link AvisoDeCajaEvent#eventoId()}, determinístico por (aprendiz,
 * envío, aviso): una reentrega del outbox, o el barrido que corre cada hora, no duplican la fila ni el push
 * (C-7). Al Admin le llega a CADA ADMIN activo (no a ALCHEMIST: la caja la lleva solo el Admin), uno por
 * persona por el índice único (usuario, tipo, origen).
 */
@Component
class AvisoDeCajaNotificationListener {

    static final String RUTA_DEL_APRENDIZ = "/caja";
    static final String RUTA_DEL_ADMIN = "/admin/caja/";
    private static final String TITULO = "Tu Caja Renaser";
    private static final String TITULO_ADMIN = "Caja Renaser";

    private final EmitirNotificacionUseCase emitir;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;

    AvisoDeCajaNotificationListener(EmitirNotificacionUseCase emitir, ParticipacionProgramaFinder participaciones,
                                    UserSummaryFinder usuarios) {
        this.emitir = emitir;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
    }

    @ApplicationModuleListener
    void on(AvisoDeCajaEvent aviso) {
        if (aviso.aviso().alAprendiz()) {
            emitir.emitir(new EmitirNotificacionCommand(aviso.aprendizId(), TipoNotificacion.HITO_PROGRAMA, TITULO,
                    cuerpoDelAprendiz(aviso), RUTA_DEL_APRENDIZ, aviso.eventoId()));
        }
        if (aviso.aviso().alAdmin()) {
            String nombre = usuarios.findById(aviso.aprendizId()).map(UserSummary::fullName).orElse("Un aprendiz");
            for (UserId admin : participaciones.usuariosActivosConRol(Set.of(UserRole.ADMIN))) {
                emitir.emitir(new EmitirNotificacionCommand(admin, TipoNotificacion.HITO_PROGRAMA, TITULO_ADMIN,
                        cuerpoDelAdmin(aviso, nombre), RUTA_DEL_ADMIN + aviso.aprendizId(), aviso.eventoId()));
            }
        }
    }

    static String cuerpoDelAprendiz(AvisoDeCajaEvent aviso) {
        return switch (aviso.aviso()) {
            case EN_REVISION, APROBADA -> "Tu caja está en revisión.";
            case ARMANDO -> "Estamos armando tu caja.";
            case EN_CAMINO -> "Tu caja va en camino. Por " + (aviso.courier() == null ? aviso.medio() : aviso.courier())
                    + ", código " + aviso.codigo() + ".";
            case RECORDATORIO -> "¿Ya te llegó tu caja? Confírmalo en la app.";
            case ENTREGADA -> "¡Tu caja llegó! Si quieres, comparte una foto en el Muro.";
            case SIN_CONFIRMAR -> throw new IllegalArgumentException("Ese aviso es para el Admin");
        };
    }

    static String cuerpoDelAdmin(AvisoDeCajaEvent aviso, String nombre) {
        return switch (aviso.aviso()) {
            case EN_REVISION -> nombre + " está lista para su Caja.";
            case SIN_CONFIRMAR -> nombre + " no confirmó su caja: salió hace 5 días.";
            default -> throw new IllegalArgumentException("Ese aviso no es para el Admin: " + aviso.aviso());
        };
    }
}
