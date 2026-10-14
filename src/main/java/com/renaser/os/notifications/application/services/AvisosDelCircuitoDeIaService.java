package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.plataforma.AvisarCircuitoDeIaUseCase;
import com.renaser.os.notifications.application.ports.out.limite.LimiteDeAvisosPorHoraPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El push a Soporte TI por cada apertura y cierre del circuito de SER (spec 002, FR-016; contrato §7), con el tope de
 * avisos por hora y por parte que pide la spec para un circuito que oscila («Circuito de IA que oscila»). Va con el tipo
 * {@code OPERACION_PLATAFORMA}: sale aunque las notificaciones estén apagadas, y tocarlo lleva a la consola.
 *
 * <p><b>Idempotente:</b> el {@code origenEventoId} sale de la parte y del instante del cambio, así una reentrega del
 * outbox choca con el índice único de {@code notificaciones} (C-7), y el tope cuenta cada cambio una sola vez.
 */
@Service
public class AvisosDelCircuitoDeIaService implements AvisarCircuitoDeIaUseCase {

    static final String RUTA_DE_LA_CONSOLA = "/soporte-ti";
    private static final Logger log = LoggerFactory.getLogger(AvisosDelCircuitoDeIaService.class);
    private static final Map<String, String> NOMBRES = Map.of("ia.chat", "Chat de SER", "ia.voz", "Voz de SER");

    private final EmitirNotificacionUseCase emitir;
    private final ParticipacionProgramaFinder participaciones;
    private final LimiteDeAvisosPorHoraPort limite;
    private final int maximoPorHora;

    public AvisosDelCircuitoDeIaService(EmitirNotificacionUseCase emitir, ParticipacionProgramaFinder participaciones,
                                        LimiteDeAvisosPorHoraPort limite,
                                        @Value("${renaser.notifications.circuito-ia.maximo-por-hora:4}")
                                        int maximoPorHora) {
        this.emitir = emitir;
        this.participaciones = participaciones;
        this.limite = limite;
        this.maximoPorHora = maximoPorHora;
    }

    @Override
    public void avisar(CambioDeCircuito cambio) {
        UUID origen = UUID.nameUUIDFromBytes(("circuito-ia:" + cambio.parte() + ":" + cambio.occurredAt())
                .getBytes(StandardCharsets.UTF_8));
        if (!limite.admite("circuito-ia:" + cambio.parte(), origen, cambio.occurredAt(), maximoPorHora)) {
            log.info("[notifications] circuito de {} {}: push omitido, ya salieron {} en la ultima hora",
                    cambio.parte(), cambio.estado(), maximoPorHora);
            return;
        }
        String titulo = "ABIERTO".equals(cambio.estado()) ? "SER se pausó sola" : "SER volvió";
        String cuerpo = NOMBRES.getOrDefault(cambio.parte(), cambio.parte()) + ": "
                + (cambio.causa() == null || cambio.causa().isBlank() ? cambio.estado() : cambio.causa()) + ".";
        for (UserId destinatario : participaciones.usuariosActivosConRol(Set.of(UserRole.IT_SUPPORT))) {
            emitir.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.OPERACION_PLATAFORMA, titulo,
                    cuerpo, RUTA_DE_LA_CONSOLA, origen));
        }
    }
}
