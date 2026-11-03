package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.ConsultarImagenDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.ImagenDeLaFormacion;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * La imagen que una formación tiene en el chat (spec 035): la de su comunidad general y la de sus grupos sin foto propia.
 *
 * <p><b>Cuál es</b> lo dice la bitácora: la última que subió Administración para la formación o, si no subió ninguna (o volvió atrás), la vigente de la
 * Escuela ({@link EstadoDePieza#deImagen}); si la Escuela tampoco tiene, no hay imagen y la app usa la suya (el fénix y la tarjeta de siempre).
 * Se pregunta en cada pedido (una o dos lecturas indexadas): un cambio vale en el acto, sin avisos entre procesos.
 *
 * <p><b>Nunca falla por una imagen rota.</b> Si la guardada ya no abre (se borró del almacenamiento, o el proceso corre con el de marcador), no hay imagen
 * y queda un {@code WARN}: la lista de chats sigue saliendo con la imagen de la app.
 *
 * <p>Sin {@code @Transactional}: abrir y escalar una imagen lleva decenas de milisegundos y no debe retener una conexión de la base.
 */
@Service
public class ImagenDeLaFormacionService implements ConsultarImagenDeLaFormacionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImagenDeLaFormacionService.class);

    private final CambiosDeBienvenidaPort cambios;
    private final ImagenDeLaFormacionPort imagenes;

    ImagenDeLaFormacionService(CambiosDeBienvenidaPort cambios, ImagenDeLaFormacionPort imagenes) {
        this.cambios = cambios;
        this.imagenes = imagenes;
    }

    @Override
    public Optional<ImagenDeLaFormacionVigente> vigente(Formacion formacion) {
        CambioDeBienvenida delaEscuela = cambios.ultimo(Formacion.POR_DEFECTO, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION).orElse(null);
        CambioDeBienvenida propio = formacion == Formacion.POR_DEFECTO ? delaEscuela
                : cambios.ultimo(formacion, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION).orElse(null);
        EstadoDePieza estado = estado(formacion, delaEscuela, propio);
        if (ImagenDeLaFormacion.SIN_IMAGEN.equals(estado.vigente())) {
            return Optional.empty();
        }
        CambioDeBienvenida ganador = estado.cambiada() ? estado.ultimoCambio() : delaEscuela;
        return Optional.of(new ImagenDeLaFormacionVigente(estado.vigente(), ganador.cambiadoEn()));
    }

    @Override
    public Optional<ImagenServida> servir(Formacion formacion) {
        return vigente(formacion).flatMap(imagen -> abrir(formacion, imagen.ruta()));
    }

    /** Cómo está la imagen en {@code formacion}: la suya, o la de la Escuela, o ninguna. */
    static EstadoDePieza estado(Formacion formacion, CambioDeBienvenida delaEscuela, CambioDeBienvenida propio) {
        return EstadoDePieza.deImagen(formacion, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION, ImagenDeLaFormacion.SIN_IMAGEN,
                delaEscuela, propio);
    }

    private Optional<ImagenServida> abrir(Formacion formacion, String ruta) {
        try {
            ImagenDeLaFormacionPort.Imagen imagen = imagenes.jpegDe(ruta);
            return Optional.of(new ImagenServida(imagen.jpeg(), imagen.huella()));
        } catch (NoSuchElementException | IllegalArgumentException sinImagen) {
            log.warn("[chat.identidad] la imagen de {} ({}) no se pudo abrir ({}): los chats usan la de la app", formacion, ruta,
                    sinImagen.getMessage());
            return Optional.empty();
        }
    }
}
