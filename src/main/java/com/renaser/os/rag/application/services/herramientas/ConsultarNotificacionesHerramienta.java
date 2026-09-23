package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort.AvisoSinLeer;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort.BandejaSinLeer;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * {@code consultar_notificaciones} (R0, solo lectura, 2026-09-23): cuantas notificaciones tiene sin
 * leer y cuales son las mas recientes.
 *
 * <p>No decide nada: la bandeja propia, la cuenta activa y la ventana de retencion las resuelve
 * {@code notifications} con los mismos casos de uso que la app, y el total es el mismo numero del
 * badge. Aca solo se arma el texto; cuanto hace que llego cada una sale del {@link Clock}
 * ({@link TiempoTranscurrido}).
 *
 * <p>El texto de las notificaciones no se loguea nunca: puede nombrar a otras personas.
 */
@Component
public class ConsultarNotificacionesHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_notificaciones";

    /** Cuantas se muestran con su texto; el resto se cuenta. Alcanza para "que me llego". */
    static final int RECIENTES = 5;
    private static final int LARGO_CUERPO = 300;

    private static final Logger log = LoggerFactory.getLogger(ConsultarNotificacionesHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve cuantas notificaciones tiene sin leer en la app y el titulo y texto de las mas recientes, "
                    + "con cuanto hace que llegaron. Usala cuando pregunte por sus avisos o notificaciones; no "
                    + "supongas que tiene o no tiene.");

    private final GestionarBandejaDeNotificacionesPort bandejaPort;
    private final Clock clock;

    public ConsultarNotificacionesHerramienta(GestionarBandejaDeNotificacionesPort bandejaPort, Clock clock) {
        this.bandejaPort = bandejaPort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            return ResultadoHerramienta.exito(texto(bandejaPort.sinLeer(actorId, RECIENTES), clock.now()));
        } catch (NoSuchElementException sinCuenta) {
            return ResultadoHerramienta.fallo("No encontre esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("No puedo consultar sus notificaciones: la cuenta esta suspendida.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer la bandeja: {}", NOMBRE, falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar sus notificaciones en este momento.");
        }
    }

    static String texto(BandejaSinLeer bandeja, Instant ahora) {
        if (bandeja.total() == 0) {
            return "No tiene notificaciones sin leer.";
        }
        StringBuilder texto = new StringBuilder("Notificaciones sin leer: ").append(bandeja.total()).append('.');
        if (!bandeja.recientes().isEmpty()) {
            texto.append("\nLas mas recientes:");
            bandeja.recientes().forEach(aviso -> texto.append("\n- ").append(linea(aviso, ahora)));
        }
        long sinMostrar = bandeja.total() - bandeja.recientes().size();
        if (sinMostrar > 0) {
            texto.append("\n(y ").append(sinMostrar).append(" mas, que puede ver en la app)");
        }
        return texto.toString();
    }

    private static String linea(AvisoSinLeer aviso, Instant ahora) {
        return aviso.titulo().strip() + ": " + TiempoTranscurrido.recortado(aviso.cuerpo(), LARGO_CUERPO)
                + " (" + TiempoTranscurrido.desde(aviso.creadoEn(), ahora) + ")";
    }
}
