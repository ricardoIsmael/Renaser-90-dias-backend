package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * La escritura de {@code proponer_marcar_notificaciones_leidas}, que solo corre cuando la persona
 * confirma con el boton. La propuso {@link PropuestaDeMarcarNotificacionesLeidas}.
 *
 * <p>Delega en {@code MarcarTodasLeidasUseCase} (via {@code rag.api.BandejaDeNotificaciones}), que
 * vuelve a exigir la cuenta activa. Es idempotente: si entre proponer y confirmar ya las leyo en la
 * app, marca 0 y lo dice.
 *
 * <p>Sin condicion de flag: una propuesta ya guardada tiene que poder confirmarse aunque el flag se
 * apague despues.
 */
@Component
public class MarcarNotificacionesLeidasConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(MarcarNotificacionesLeidasConfirmable.class);

    private final GestionarBandejaDeNotificacionesPort bandejaPort;

    public MarcarNotificacionesLeidasConfirmable(GestionarBandejaDeNotificacionesPort bandejaPort) {
        this.bandejaPort = bandejaPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeMarcarNotificacionesLeidas.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        int marcadas;
        try {
            marcadas = bandejaPort.marcarTodasLeidas(actorId);
        } catch (NoSuchElementException | NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no se cambio nada.");
        } catch (RuntimeException falla) {
            log.info("[rag] {} no pudo marcar la bandeja: {}", herramienta(), falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No se pudieron marcar las notificaciones en este momento.");
        }
        return ResultadoHerramienta.exito(switch (marcadas) {
            case 0 -> "No quedaban notificaciones sin leer: no se cambio nada.";
            case 1 -> "Listo: 1 notificacion marcada como leida.";
            default -> "Listo: " + marcadas + " notificaciones marcadas como leidas.";
        });
    }
}
