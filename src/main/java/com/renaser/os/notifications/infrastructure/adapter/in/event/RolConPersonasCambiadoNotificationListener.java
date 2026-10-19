package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.plataforma.api.RolConPersonasCambiadoEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Avisa por push a Administración que Soporte TI cambió un rol personalizado con personas (spec 002, US7 escenario 6;
 * D-272; decisión del dueño del 2026-10-14). Mismo camino que el aviso de un interruptor
 * ({@link FuncionalidadCambiadaNotificationListener}): por evento, después del commit, tipo {@code OPERACION_PLATAFORMA}
 * (sale aunque las notificaciones estén apagadas).
 *
 * <p><b>A quién:</b> a todos los ADMIN y ALQUIMISTA activos, y a nadie más. Las cuentas de prueba de «ver como» no lo
 * reciben: {@code usuariosActivosConRol} ya no las trae y {@code NotificacionService} las corta igual (D-273). Sin ruta
 * en la app: Administración no tiene la consola, y el nombre del rol ya se ve en «Equipo y roles».
 *
 * <p><b>Idempotente:</b> el {@code origenEventoId} sale del rol y del instante del cambio, así una reentrega del outbox
 * choca con el índice único de {@code notificaciones} (C-7) y no repite el push.
 */
@Component
class RolConPersonasCambiadoNotificationListener {

    static final String TITULO = "Soporte TI cambió un rol";
    private static final Set<UserRole> ADMINISTRACION = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final EmitirNotificacionUseCase emitir;
    private final ParticipacionProgramaFinder participaciones;

    RolConPersonasCambiadoNotificationListener(EmitirNotificacionUseCase emitir,
                                               ParticipacionProgramaFinder participaciones) {
        this.emitir = emitir;
        this.participaciones = participaciones;
    }

    @ApplicationModuleListener
    void on(RolConPersonasCambiadoEvent evento) {
        UUID origen = UUID.nameUUIDFromBytes(("rol-personalizado:" + evento.rolId() + ":" + evento.occurredAt())
                .getBytes(StandardCharsets.UTF_8));
        String cuerpo = cuerpo(evento);
        for (UserId destinatario : participaciones.usuariosActivosConRol(ADMINISTRACION)) {
            emitir.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.OPERACION_PLATAFORMA, TITULO,
                    cuerpo, null, origen));
        }
    }

    /** «Soporte TI cambió el rol «Mentor junior» (3 personas): lo desactivó: …. Motivo: …». */
    static String cuerpo(RolConPersonasCambiadoEvent evento) {
        StringBuilder texto = new StringBuilder("Soporte TI cambió el rol «").append(evento.nombre()).append("» (")
                .append(evento.personas()).append(evento.personas() == 1 ? " persona)" : " personas)");
        if (evento.resumen() != null && !evento.resumen().isBlank()) {
            texto.append(": ").append(evento.resumen());
        }
        texto.append('.');
        if (evento.motivo() != null && !evento.motivo().isBlank()) {
            texto.append(" Motivo: ").append(evento.motivo());
        }
        return texto.toString();
    }
}
