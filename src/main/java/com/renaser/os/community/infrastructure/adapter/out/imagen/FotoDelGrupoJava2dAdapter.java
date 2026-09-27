package com.renaser.os.community.infrastructure.adapter.out.imagen;

import com.renaser.os.community.application.ports.out.celula.PrepararFotoDelGrupoPort;
import com.renaser.os.community.domain.model.celula.FotoSubidaDelGrupo;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * La foto de un grupo preparada con Java2D (D-212): se lee, se recorta al centro en cuadrado, se lleva a
 * {@link #LADO} px y se reescribe como JPEG. Lo que queda guardado lo generó el servidor: sin metadatos
 * del teléfono y siempre del mismo tamaño (unos 50 KB).
 *
 * <p><b>Memoria acotada.</b> Antes de leer los píxeles se miran las medidas de la cabecera: más de
 * {@link #LADO_MAXIMO} px por lado es un 400, y una imagen grande se lee submuestreada para no pasar de
 * unos {@link #LADO_DE_LECTURA} px por lado. Así un archivo chico con medidas absurdas no puede pedirle
 * cientos de MB a la JVM, que en producción corre con tope de memoria (V-8).
 */
@Component
class FotoDelGrupoJava2dAdapter implements PrepararFotoDelGrupoPort {

    /** El lado de la foto guardada. El avatar más grande del chat son 120 px lógicos (360 físicos). */
    static final int LADO = 512;
    /** Medidas de cabecera más allá de esto no son una foto de teléfono: 400. */
    static final int LADO_MAXIMO = 10_000;
    /** Hasta dónde se leen los píxeles; una más grande se lee submuestreada. */
    static final int LADO_DE_LECTURA = 1_024;
    private static final float CALIDAD_JPEG = 0.85f;

    @Override
    public byte[] comoJpegCuadrado(FotoSubidaDelGrupo foto) {
        return comoJpeg(cuadradaDe(leer(foto.contenido())));
    }

    private static BufferedImage leer(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new IllegalArgumentException("No se pudo leer la foto como imagen");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                return leerAcotada(lector);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException propia) {
                throw propia;
            }
            throw new IllegalArgumentException("No se pudo leer la foto como imagen", e);
        }
    }

    private static BufferedImage leerAcotada(ImageReader lector) throws IOException {
        int ancho = lector.getWidth(0);
        int alto = lector.getHeight(0);
        if (ancho <= 0 || alto <= 0 || ancho > LADO_MAXIMO || alto > LADO_MAXIMO) {
            throw new IllegalArgumentException("La foto tiene medidas que no son de una foto: " + ancho + "×" + alto);
        }
        ImageReadParam parametros = lector.getDefaultReadParam();
        int paso = Math.max(1, (int) Math.ceil(Math.max(ancho, alto) / (double) LADO_DE_LECTURA));
        parametros.setSourceSubsampling(paso, paso, 0, 0);
        return lector.read(0, parametros);
    }

    /** Recorte cuadrado al centro, llevado a {@link #LADO}; sin transparencia (el JPEG no tiene), sobre blanco. */
    private static BufferedImage cuadradaDe(BufferedImage original) {
        int lado = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - lado) / 2;
        int y = (original.getHeight() - lado) / 2;
        BufferedImage cuadrada = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = cuadrada.createGraphics();
        try {
            lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            lienzo.setColor(Color.WHITE);
            lienzo.fillRect(0, 0, LADO, LADO);
            lienzo.drawImage(original, 0, 0, LADO, LADO, x, y, x + lado, y + lado, null);
        } finally {
            lienzo.dispose();
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
            throw new UncheckedIOException("No se pudo codificar la foto del grupo", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }
}
