package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerTarjetaDeMuestraUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.PortadaDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Cambiar la portada de la tarjeta de bienvenida desde la app y verla antes de usarla (D-210).
 *
 * <p><b>Dónde queda la imagen:</b> en el mismo almacenamiento que las fotos de evidencia
 * ({@link AlmacenamientoPort}), bajo {@link PortadaDeBienvenida#PREFIJO_RUTA}, subida por el teléfono con
 * URL prefirmada como la portada de un evento. En local el almacenamiento es de marcador (no se prende
 * S3: el bucket por defecto es el de producción, E-305): ahí la URL sale como marcador, el teléfono avisa
 * y confirmar responde 409. Los textos sí se cambian en local.
 *
 * <p><b>Revisar antes de usar.</b> La imagen se abre y se revisa (formato, peso, medidas, que el nombre
 * se lea) al pedir su vista previa y otra vez al confirmarla: nada que no pasó la revisión llega a una
 * tarjeta. Abrir la imagen va sin transacción (no retiene una conexión de la base); lo único que escribe
 * es el INSERT de la bitácora.
 *
 * <p><b>Lo que no se hace:</b> borrar del almacenamiento las imágenes rechazadas o reemplazadas. Pesan
 * poco, la bitácora apunta a las viejas, y una que no se confirmó queda como la portada de un evento que
 * no se confirmó.
 */
@Service
public class PortadaDeBienvenidaAdminService implements CambiarPortadaDeBienvenidaUseCase, VerTarjetaDeMuestraUseCase {

    /** Igual que la portada de un evento: la URL es una credencial de escritura, dura lo que la subida. */
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final int LARGO_MAXIMO_DEL_NOMBRE = 40;

    private final BienvenidaParaAdministrar bienvenida;
    private final CambiosDeBienvenidaPort cambios;
    private final PortadaDeBienvenidaPort portadas;
    private final DibujarBienvenidaPort dibujante;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator idGenerator;
    private final Clock clock;

    PortadaDeBienvenidaAdminService(BienvenidaParaAdministrar bienvenida, CambiosDeBienvenidaPort cambios,
                                    PortadaDeBienvenidaPort portadas, DibujarBienvenidaPort dibujante,
                                    AlmacenamientoPort almacenamiento, IdGenerator idGenerator, Clock clock) {
        this.bienvenida = bienvenida;
        this.cambios = cambios;
        this.portadas = portadas;
        this.dibujante = dibujante;
        this.almacenamiento = almacenamiento;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public UrlDeSubida solicitarSubida(UserId actorId, String tipoContenido) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        PortadaDeBienvenida.exigirTipoDeContenido(tipoContenido);
        String ruta = PortadaDeBienvenida.PREFIJO_RUTA + idGenerator.newId();
        return new UrlDeSubida(almacenamiento.firmarSubida(ruta, tipoContenido.strip(), VALIDEZ_URL_SUBIDA), ruta);
    }

    @Override
    public BienvenidaEditable confirmar(UserId actorId, String ruta) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        String propia = PortadaDeBienvenida.exigirRutaPropia(ruta);
        if (!propia.equals(bienvenida.estado(PiezaDeBienvenida.PORTADA).vigente())) {
            revisar(propia);
            cambios.registrar(CambioDeBienvenida.portada(propia, actorId, clock.now()));
        }
        return bienvenida.leer();
    }

    @Override
    public BienvenidaEditable volverALaOriginal(UserId actorId) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        if (bienvenida.estado(PiezaDeBienvenida.PORTADA).cambiada()) {
            cambios.registrar(CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.PORTADA, actorId, clock.now()));
        }
        return bienvenida.leer();
    }

    @Override
    public byte[] muestra(UserId actorId, String nombre, String portadaCandidata) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        String primerNombre = primerNombreDeMuestra(nombre);
        if (portadaCandidata == null || portadaCandidata.isBlank()) {
            return dibujante.dibujar(primerNombre);
        }
        String propia = PortadaDeBienvenida.exigirRutaPropia(portadaCandidata);
        revisar(propia);
        return dibujante.dibujar(primerNombre, propia);
    }

    /** Sin almacenamiento de verdad no hay imagen subida que abrir: 409, con el motivo. */
    private void revisar(String ruta) {
        if (!almacenamiento.guardaObjetos()) {
            throw new IllegalStateException("Este servidor no tiene dónde guardar imágenes, así que la portada "
                    + "no se puede cambiar desde acá.");
        }
        portadas.revisar(ruta);
    }

    /** Como con una persona de verdad: la tarjeta lleva solo su primer nombre ({@link PrimerNombre}). */
    private static String primerNombreDeMuestra(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.codePointCount(0, limpio.length()) > LARGO_MAXIMO_DEL_NOMBRE) {
            throw new IllegalArgumentException("El nombre de ejemplo puede tener hasta " + LARGO_MAXIMO_DEL_NOMBRE
                    + " letras.");
        }
        return PrimerNombre.de(limpio);
    }
}
