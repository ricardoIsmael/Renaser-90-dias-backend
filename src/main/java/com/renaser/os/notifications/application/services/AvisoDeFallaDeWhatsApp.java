package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Le avisa a Administracion y a Alquimista que el WhatsApp de los eventos dejo de salir por algo que les toca arreglar (spec 041, FR-024): la clave de
 * YCloud, la plantilla, el saldo de la billetera o el tope mensual. Reusa {@link TipoNotificacion#OPERACION_PLATAFORMA}, sin tipo nuevo.
 *
 * <p><b>Un aviso por dia y por causa.</b> El {@code origenEventoId} sale del dia (en la zona del programa) y de la causa, asi que los miles de envios
 * que fallan por lo mismo chocan con el indice unico de {@code notificaciones} (C-7) y no inundan el telefono. Como no hay tope mensual de fabrica,
 * este aviso es la forma de enterarse de que hay que recargar la billetera.
 */
@Component
class AvisoDeFallaDeWhatsApp {

    static final ZoneId ZONA_DEL_PROGRAMA = ZoneId.of("America/Lima");
    private static final Logger log = LoggerFactory.getLogger(AvisoDeFallaDeWhatsApp.class);
    private static final Set<UserRole> QUIEN_LO_ARREGLA = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);
    private static final String TITULO = "El WhatsApp de los eventos no está saliendo";

    private final EmitirNotificacionUseCase emitir;
    private final ParticipacionProgramaFinder participaciones;
    private final Clock clock;

    AvisoDeFallaDeWhatsApp(EmitirNotificacionUseCase emitir, ParticipacionProgramaFinder participaciones, Clock clock) {
        this.emitir = emitir;
        this.participaciones = participaciones;
        this.clock = clock;
    }

    /** Solo las causas que les tocan a ellos; el resto queda anotado en la entrega y nada mas. */
    void avisar(CausaDeFalla causa) {
        if (!causa.leTocaAAdministracion()) {
            return;
        }
        String dia = clock.now().atZone(ZONA_DEL_PROGRAMA).toLocalDate().toString();
        UUID origen = UUID.nameUUIDFromBytes(("whatsapp-eventos|" + causa + "|" + dia).getBytes(StandardCharsets.UTF_8));
        try {
            for (UserId destinatario : participaciones.usuariosActivosConRol(QUIEN_LO_ARREGLA)) {
                emitir.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.OPERACION_PLATAFORMA, TITULO,
                        causa.aviso() + " Los recordatorios siguen llegando por la app y el chat.", null, origen));
            }
        } catch (RuntimeException sinAviso) {
            // Avisar es un extra: nunca hace que el envio falle ni se reintente.
            log.warn("[notifications.evento-whatsapp] no se pudo avisar a Administracion de {}: {}", causa, sinAviso.toString());
        }
    }
}
