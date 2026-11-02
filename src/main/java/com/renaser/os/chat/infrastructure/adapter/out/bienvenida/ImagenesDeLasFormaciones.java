package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort;
import com.renaser.os.chat.domain.model.bienvenida.ImagenDeLaFormacion;
import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

/**
 * Las imágenes de las formaciones (spec 035): se abren, se llevan a un cuadrado de {@link ImagenDeLaFormacion#LADO_SERVIDO} px y se sirven como JPEG.
 *
 * <p><b>Abrir sin arriesgar la memoria.</b> Se reutiliza {@link ImagenDePortada#abrir}: lee las medidas de la cabecera antes de decodificar y decodifica
 * salteando píxeles, así una foto de 8000 px no ocupa 190 MB (el contenedor de producción tiene tope de memoria, V-8). Qué es aceptable (JPEG o PNG, peso y
 * medidas) lo decide {@link PortadaDeBienvenida}; a diferencia de la portada, acá NO se mide si un nombre se lee encima: es un avatar. Se usa el centro
 * cuadrado de la imagen y lo transparente de un PNG queda blanco.
 *
 * <p><b>Qué se guarda en memoria:</b> el JPEG ya servido (unos 40–80 KB), no los píxeles, y como mucho {@link #SERVIDAS} (una por formación). Una ruta no se
 * reescribe nunca (cada subida lleva un id nuevo), así que lo guardado no envejece.
 */
@Component
class ImagenesDeLasFormaciones implements ImagenDeLaFormacionPort {

    static final int SERVIDAS = 3;
    private static final float CALIDAD_JPEG = 0.85f;

    private final AlmacenamientoPort almacenamiento;
    private final Cache<String, Imagen> servidas = Caffeine.newBuilder().maximumSize(SERVIDAS).build();

    ImagenesDeLasFormaciones(AlmacenamientoPort almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    @Override
    public void revisar(String ruta) {
        servidas.put(ruta, preparar(ImagenDeLaFormacion.exigirRutaPropia(ruta)));
    }

    @Override
    public Imagen jpegDe(String ruta) {
        Imagen guardada = servidas.getIfPresent(ruta);
        if (guardada != null) {
            return guardada;
        }
        Imagen nueva = preparar(ImagenDeLaFormacion.exigirRutaPropia(ruta));
        servidas.put(ruta, nueva);
        return nueva;
    }

    private Imagen preparar(String ruta) {
        byte[] contenido = almacenamiento.leer(ruta, PortadaDeBienvenida.PESO_MAXIMO_EN_BYTES)
                .orElseThrow(() -> new NoSuchElementException("No encontramos la imagen subida: vuelve a elegirla."));
        byte[] jpeg = comoJpeg(alLado(ImagenDePortada.abrir(contenido)));
        return new Imagen(jpeg, TarjetasConNombreEnMemoria.huella(jpeg));
    }

    private static BufferedImage alLado(BufferedImage lienzo) {
        int lado = ImagenDeLaFormacion.LADO_SERVIDO;
        BufferedImage cuadrada = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cuadrada.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(lienzo, 0, 0, lado, lado, null);
        } finally {
            g.dispose();
        }
        return cuadrada;
    }

    private static byte[] comoJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo codificar la imagen de la formación", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }
}
