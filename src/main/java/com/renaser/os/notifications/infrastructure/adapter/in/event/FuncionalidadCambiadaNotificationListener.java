package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Avisa por push que Soporte TI cambió un interruptor (spec 002, FR-008; contrato §7).
 *
 * <p><b>A quién:</b> a las cuentas IT_SUPPORT activas siempre; al Admin y al Alquimista activos solo si la
 * funcionalidad es crítica o quedó apagada para todos (la decisión del dueño: «avisar al Alquimista y al Admin cuando
 * se apaga algo para todos»).
 *
 * <p><b>Idempotente:</b> el {@code origenEventoId} sale de la clave y del instante del cambio (contrato §7), así una
 * reentrega del outbox choca con el índice único de {@code notificaciones} (C-7) y no repite el push.
 *
 * <p><b>Qué dice:</b> qué funcionalidad, cómo quedó, quién y por qué. Tocarlo lleva a Soporte TI a su consola; al
 * Admin y al Alquimista no los lleva a ningún lado (no tienen consola).
 */
@Component
class FuncionalidadCambiadaNotificationListener {

    static final String TITULO = "Soporte TI cambió una funcionalidad";
    static final String RUTA_DE_LA_CONSOLA = "/soporte-ti";
    private static final Set<UserRole> ADMINISTRACION = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final EmitirNotificacionUseCase emitir;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;

    FuncionalidadCambiadaNotificationListener(EmitirNotificacionUseCase emitir, ParticipacionProgramaFinder participaciones,
                                              UserSummaryFinder usuarios) {
        this.emitir = emitir;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
    }

    @ApplicationModuleListener
    void on(FuncionalidadCambiadaEvent evento) {
        UUID origen = UUID.nameUUIDFromBytes(("plataforma:" + evento.clave() + ":" + evento.occurredAt())
                .getBytes(StandardCharsets.UTF_8));
        String cuerpo = cuerpo(evento);
        Set<UserId> soporte = new LinkedHashSet<>(participaciones.usuariosActivosConRol(Set.of(UserRole.IT_SUPPORT)));
        for (UserId destinatario : soporte) {
            emitir.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.OPERACION_PLATAFORMA, TITULO,
                    cuerpo, RUTA_DE_LA_CONSOLA, origen));
        }
        if (evento.critica() || evento.apagadaParaTodos()) {
            for (UserId destinatario : participaciones.usuariosActivosConRol(ADMINISTRACION)) {
                if (!soporte.contains(destinatario)) {
                    emitir.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.OPERACION_PLATAFORMA,
                            TITULO, cuerpo, null, origen));
                }
            }
        }
    }

    /** «Chat de grupo: Apagada para todos. Lo hizo Ana. Motivo: …». Sin el aviso: eso lo ve la gente en la app. */
    private String cuerpo(FuncionalidadCambiadaEvent evento) {
        String nombre = evento.nombre() == null || evento.nombre().isBlank() ? evento.clave() : evento.nombre();
        String resumen = evento.resumen() == null || evento.resumen().isBlank() ? "cambió" : evento.resumen();
        String quien = evento.actor() == null ? null : usuarios.findById(evento.actor())
                .map(UserSummary::fullName).filter(n -> !n.isBlank()).orElse(null);
        StringBuilder texto = new StringBuilder(nombre).append(": ").append(resumen).append('.');
        if (quien != null) {
            texto.append(" Lo hizo ").append(quien).append('.');
        }
        if (evento.motivo() != null && !evento.motivo().isBlank()) {
            texto.append(" Motivo: ").append(evento.motivo());
        }
        return texto.toString();
    }
}
