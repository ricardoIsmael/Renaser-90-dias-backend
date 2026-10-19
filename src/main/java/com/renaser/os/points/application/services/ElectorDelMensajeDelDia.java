package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.out.semaforo.mensaje.AzarPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.DatosDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajesVistos;
import com.renaser.os.shared.domain.Clock;

import java.util.Optional;

/**
 * Elige un mensaje NUEVO del grupo para un canal y lo deja guardado (D-277, D-281). Es lo que comparten el mensaje de
 * Hoy y el del chat: la regla vive en {@link EleccionDelMensaje} y la persistencia en {@link MensajesElegidosPort}; acá
 * solo se juntan, para que los dos canales no copien el mismo cruce de lecturas.
 *
 * <p>No es un bean: cada servicio crea el suyo con su canal.
 */
final class ElectorDelMensajeDelDia {

    private final CatalogoDeMensajesPort catalogo;
    private final MensajesElegidosPort elegidos;
    private final EleccionDelMensaje eleccion;
    private final Clock clock;

    ElectorDelMensajeDelDia(CatalogoDeMensajesPort catalogo, MensajesElegidosPort elegidos, AzarPort azar,
                            Clock clock, CanalDelMensaje canal) {
        this.catalogo = catalogo;
        this.elegidos = elegidos;
        this.eleccion = new EleccionDelMensaje(azar::indice, canal);
        this.clock = clock;
    }

    /** Vacío si en el grupo no queda ningún mensaje posible; no guarda nada en ese caso. */
    Optional<MensajeDelSemaforo> elegirYGuardar(ClaveDeLaEleccion clave, DatosDelMensaje datos, MensajesVistos vistos) {
        return eleccion.elegir(catalogo.activosDe(clave.grupo()), datos, vistos)
                .map(elegido -> guardar(clave, elegido));
    }

    /** Guarda la elección; si otra lectura se adelantó, queda la suya, y las dos personas ven el mismo mensaje. */
    private MensajeDelSemaforo guardar(ClaveDeLaEleccion clave, MensajeDelSemaforo elegido) {
        String guardado = elegidos.registrar(clave, elegido.id(), clock.now());
        return guardado.equals(elegido.id()) ? elegido : catalogo.porId(guardado).orElse(elegido);
    }
}
