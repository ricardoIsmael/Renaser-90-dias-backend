package com.renaser.os.shared.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Qué se puede subir con una URL prefirmada de cada uso, y cuánto puede pesar (hallazgo N-3 de la auditoría del 20/11).
 *
 * <p>El {@code Content-Type} de una subida prefirmada lo elige el cliente y S3 solo exige que el PUT repita el que se firmó:
 * sin una lista, cualquiera firmaba {@code text/html} o {@code image/svg+xml} y quedaba con contenido activo servido desde
 * nuestro bucket (en el avatar, además, con URL pública). Cada uso declara acá los tipos que la app manda hoy; lo demás se
 * rechaza al firmar con {@code TIPO_DE_ARCHIVO_NO_PERMITIDO} (400).
 *
 * <p><b>El tope no se firma.</b> La app no manda el tamaño al pedir la URL, así que no hay un {@code contentLength} que firmar
 * sin romper a las versiones ya publicadas. El peso se comprueba al confirmar la subida, mirando el objeto que ya está en el
 * almacenamiento ({@code AlmacenamientoPort#pesoDe}); si pasa del tope se borra y se rechaza con {@code ARCHIVO_DEMASIADO_GRANDE}.
 * Nunca se admiten {@code svg}, {@code html} ni nada que un navegador ejecute.
 */
public enum TipoDeSubida {

    /** Evidencia de hábitos: lo que la app captura hoy (foto JPEG, video de cámara o galería, audio grabado). */
    EVIDENCIA(
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif",
                    "video/mp4", "video/quicktime", "video/x-m4v", "video/webm", "video/3gpp", "video/3gpp2",
                    "video/x-matroska", "video/hevc",
                    "audio/m4a", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/mpeg", "audio/mp3", "audio/3gpp",
                    "audio/x-caf", "audio/wav", "audio/x-wav", "audio/webm", "audio/ogg")),

    /** Foto de perfil. Su URL de lectura es pública: solo imágenes de mapa de bits. */
    AVATAR(Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"));

    private static final long MB = 1024L * 1024L;
    private static final long TOPE_IMAGEN = 20 * MB;
    private static final long TOPE_AUDIO = 60 * MB;
    private static final long TOPE_VIDEO = 300 * MB;
    private static final long TOPE_AVATAR = 10 * MB;

    private final Set<String> tiposPermitidos;

    TipoDeSubida(Set<String> tiposPermitidos) {
        this.tiposPermitidos = tiposPermitidos;
    }

    /** Rechaza (400, {@code TIPO_DE_ARCHIVO_NO_PERMITIDO}) un tipo que este uso no admite. */
    public void exigirTipoPermitido(String tipoContenido) {
        if (!permite(tipoContenido)) {
            throw RechazoConCodigoException.invalido("TIPO_DE_ARCHIVO_NO_PERMITIDO",
                    "Ese tipo de archivo no se puede subir aquí.");
        }
    }

    public boolean permite(String tipoContenido) {
        return tiposPermitidos.contains(tipoBase(tipoContenido));
    }

    /** Lo máximo que puede pesar un archivo de este uso, en bytes, según su tipo. */
    public long pesoMaximo(String tipoContenido) {
        if (this == AVATAR) {
            return TOPE_AVATAR;
        }
        String base = tipoBase(tipoContenido);
        if (base.startsWith("video/")) {
            return TOPE_VIDEO;
        }
        return base.startsWith("audio/") ? TOPE_AUDIO : TOPE_IMAGEN;
    }

    /** Rechaza (400, {@code ARCHIVO_DEMASIADO_GRANDE}) un archivo que ya subió y pesa más que el tope de su tipo. */
    public void exigirPeso(long bytes, String tipoContenido) {
        long tope = pesoMaximo(tipoContenido);
        if (bytes > tope) {
            throw RechazoConCodigoException.invalido("ARCHIVO_DEMASIADO_GRANDE",
                    "El archivo pesa demasiado: el máximo es de " + (tope / MB) + " MB.");
        }
    }

    /** El tipo sin parámetros ({@code ; codecs=…}), en minúsculas y sin espacios: lo que se compara con la lista. */
    private static String tipoBase(String tipoContenido) {
        if (tipoContenido == null) {
            return "";
        }
        int coma = tipoContenido.indexOf(';');
        String base = coma < 0 ? tipoContenido : tipoContenido.substring(0, coma);
        return base.strip().toLowerCase(Locale.ROOT);
    }
}
