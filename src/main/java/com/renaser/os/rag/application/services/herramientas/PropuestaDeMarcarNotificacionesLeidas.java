package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * {@code proponer_marcar_notificaciones_leidas} (R2 de bajo riesgo, propuesta; 2026-09-23): marcar
 * como leidas todas sus notificaciones. No escribe: deja una propuesta y la persona confirma con el
 * boton. La escritura es {@link MarcarNotificacionesLeidasConfirmable}, que llama al mismo caso de
 * uso que {@code PUT /api/v1/notifications/read-all}.
 *
 * <p>Antes de proponer mira si hay alguna sin leer, para no ofrecer un boton que no cambia nada.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones} prendido: sin botones
 * en la app, una propuesta no tiene quien la confirme.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeMarcarNotificacionesLeidas implements HerramientaAgente {

    public static final String NOMBRE = "proponer_marcar_notificaciones_leidas";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeMarcarNotificacionesLeidas.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Propone marcar como leidas TODAS sus notificaciones sin leer. NO las marca: deja una propuesta y la "
                    + "persona tiene que tocar Confirmar en la app. Nunca digas que ya quedaron leidas. Usala solo "
                    + "si la persona pidio limpiar o marcar como leidas sus notificaciones.");

    private final GestionarBandejaDeNotificacionesPort bandejaPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeMarcarNotificacionesLeidas(GestionarBandejaDeNotificacionesPort bandejaPort,
                                                 ProponerAccionUseCase proponerAccion) {
        this.bandejaPort = bandejaPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        long sinLeer;
        try {
            sinLeer = bandejaPort.sinLeer(actorId, 0).total();
        } catch (NoSuchElementException | NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo tocar sus notificaciones: la cuenta esta suspendida.");
        }
        if (sinLeer == 0) {
            return ResultadoHerramienta.fallo("No tiene notificaciones sin leer: no hay nada que marcar.");
        }
        String resumen = resumenPara(sinLeer);
        try {
            proponerAccion.proponer(actorId, InvocacionHerramienta.sinArgumentos(NOMBRE), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    /** Lo que ve la persona junto a los botones. Las que lleguen antes de confirmar tambien se marcan. */
    static String resumenPara(long sinLeer) {
        return sinLeer == 1 ? "Marcar como leida tu notificacion sin leer"
                : "Marcar como leidas tus " + sinLeer + " notificaciones sin leer";
    }
}
