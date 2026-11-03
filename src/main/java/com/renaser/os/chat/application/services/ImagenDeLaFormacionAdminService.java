package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.AdministrarImagenDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.UltimoCambio;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.ConsultarImagenDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.ImagenDeLaFormacion;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ver y cambiar la imagen de una formación en el chat desde la app (spec 035), con el camino de la portada de la bienvenida: URL prefirmada, el teléfono sube la
 * imagen directo al almacenamiento y se confirma la ruta. La imagen se abre y se revisa al pedir su vista previa y otra vez al confirmarla; nada que no
 * pasó la revisión llega a un chat. Abrirla va sin transacción; lo único que escribe es el INSERT de la bitácora.
 *
 * <p>Lo que no se hace: borrar del almacenamiento las imágenes rechazadas o reemplazadas (pesan poco y la bitácora apunta a las viejas).
 */
@Service
public class ImagenDeLaFormacionAdminService implements AdministrarImagenDeLaFormacionUseCase {

    /** Igual que la portada de la bienvenida: la URL es una credencial de escritura, dura lo que la subida. */
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final BienvenidaParaAdministrar administracion;
    private final CambiosDeBienvenidaPort cambios;
    private final ImagenDeLaFormacionPort imagenes;
    private final ConsultarImagenDeLaFormacionUseCase vigentes;
    private final AlmacenamientoPort almacenamiento;
    private final UserSummaryFinder usuarios;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ImagenDeLaFormacionAdminService(BienvenidaParaAdministrar administracion, CambiosDeBienvenidaPort cambios,
                                    ImagenDeLaFormacionPort imagenes, ConsultarImagenDeLaFormacionUseCase vigentes,
                                    AlmacenamientoPort almacenamiento, UserSummaryFinder usuarios, IdGenerator idGenerator,
                                    Clock clock) {
        this.administracion = administracion;
        this.cambios = cambios;
        this.imagenes = imagenes;
        this.vigentes = vigentes;
        this.almacenamiento = almacenamiento;
        this.usuarios = usuarios;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public ImagenEditable ver(UserId actorId, Formacion formacion) {
        administracion.exigirQuePuedaCambiarla(actorId);
        return leer(formacion);
    }

    @Override
    public CambiarPortadaDeBienvenidaUseCase.UrlDeSubida solicitarSubida(UserId actorId, String tipoContenido) {
        administracion.exigirQuePuedaCambiarla(actorId);
        ImagenDeLaFormacion.exigirTipoDeContenido(tipoContenido);
        String ruta = ImagenDeLaFormacion.PREFIJO_RUTA + idGenerator.newId();
        return new CambiarPortadaDeBienvenidaUseCase.UrlDeSubida(
                almacenamiento.firmarSubida(ruta, tipoContenido.strip(), VALIDEZ_URL_SUBIDA), ruta);
    }

    @Override
    public ImagenEditable confirmar(UserId actorId, Formacion formacion, String ruta) {
        administracion.exigirQuePuedaCambiarla(actorId);
        String propia = ImagenDeLaFormacion.exigirRutaPropia(ruta);
        EstadoDePieza estado = estadoDe(formacion);
        if (!(estado.cambiada() && propia.equals(estado.vigente()))) {
            revisar(propia);
            cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(formacion, propia, actorId, clock.now()));
        }
        return leer(formacion);
    }

    @Override
    public ImagenEditable volverALaOriginal(UserId actorId, Formacion formacion) {
        administracion.exigirQuePuedaCambiarla(actorId);
        if (estadoDe(formacion).cambiada()) {
            cambios.registrar(CambioDeBienvenida.volverAlOriginal(formacion, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION, actorId,
                    clock.now()));
        }
        return leer(formacion);
    }

    @Override
    public byte[] vista(UserId actorId, Formacion formacion, String rutaCandidata) {
        administracion.exigirQuePuedaCambiarla(actorId);
        if (rutaCandidata == null || rutaCandidata.isBlank()) {
            return vigentes.servir(formacion).map(ConsultarImagenDeLaFormacionUseCase.ImagenServida::jpeg).orElse(null);
        }
        String propia = ImagenDeLaFormacion.exigirRutaPropia(rutaCandidata);
        revisar(propia);
        return imagenes.jpegDe(propia).jpeg();
    }

    /** Sin almacenamiento de verdad no hay imagen subida que abrir: 409, con el motivo. */
    private void revisar(String ruta) {
        if (!almacenamiento.guardaObjetos()) {
            throw new IllegalStateException("Este servidor no tiene dónde guardar imágenes, así que la imagen "
                    + "no se puede cambiar desde acá.");
        }
        imagenes.revisar(ruta);
    }

    private ImagenEditable leer(Formacion formacion) {
        EstadoDePieza estado = estadoDe(formacion);
        boolean hayImagen = vigentes.vigente(formacion).isPresent();
        boolean heredada = formacion != Formacion.POR_DEFECTO && !estado.cambiada() && hayImagen;
        return new ImagenEditable(formacion, hayImagen, estado.cambiada(), heredada, almacenamiento.guardaObjetos(),
                ultimoCambio(estado));
    }

    private EstadoDePieza estadoDe(Formacion formacion) {
        CambioDeBienvenida delaEscuela = cambios.ultimo(Formacion.POR_DEFECTO, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION).orElse(null);
        CambioDeBienvenida propio = formacion == Formacion.POR_DEFECTO ? delaEscuela
                : cambios.ultimo(formacion, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION).orElse(null);
        return ImagenDeLaFormacionService.estado(formacion, delaEscuela, propio);
    }

    private UltimoCambio ultimoCambio(EstadoDePieza estado) {
        return estado.ultimo().map(cambio -> new UltimoCambio(
                        cambio.quien().flatMap(usuarios::findById).map(UserSummary::fullName).orElse(null),
                        cambio.cambiadoEn(), cambio.esVueltaAlOriginal()))
                .orElse(null);
    }
}
