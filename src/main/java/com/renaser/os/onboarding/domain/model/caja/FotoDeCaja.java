package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Las dos fotos de un envío: la de la caja armada y la del comprobante (spec §4). Van a
 * {@code medias_onboarding} a nombre del APRENDIZ, bajo {@code onboarding/<aprendizId>/caja/}: la ruta la
 * arma el servidor, nunca el cliente.
 */
public enum FotoDeCaja {

    ARMADA(TipoPasoCaja.FOTO, AccionDeCaja.FIJAR_FOTO, "caja_foto", PasoDeCaja.FOTO_MEDIA_ID, PasoDeCaja.FOTO_RUTA),
    COMPROBANTE(TipoPasoCaja.COMPROBANTE, AccionDeCaja.FIJAR_COMPROBANTE, "caja_comprobante",
            PasoDeCaja.COMPROBANTE_MEDIA_ID, PasoDeCaja.COMPROBANTE_RUTA);

    public static final Set<String> TIPOS_DE_CONTENIDO = Set.of("image/jpeg", "image/png");
    public static final long PESO_MAXIMO_EN_BYTES = 10L * 1024 * 1024;
    private static final int LARGO_MAXIMO_DE_RUTA = 300;

    private final TipoPasoCaja paso;
    private final AccionDeCaja accion;
    private final String clave;
    private final String claveMediaEnvio;
    private final String claveRutaEnvio;

    FotoDeCaja(TipoPasoCaja paso, AccionDeCaja accion, String clave, String claveMediaEnvio, String claveRutaEnvio) {
        this.paso = paso;
        this.accion = accion;
        this.clave = clave;
        this.claveMediaEnvio = claveMediaEnvio;
        this.claveRutaEnvio = claveRutaEnvio;
    }

    public TipoPasoCaja paso() {
        return paso;
    }

    public AccionDeCaja accion() {
        return accion;
    }

    /** La {@code clave_pregunta} con que queda en {@code medias_onboarding}. */
    public String clave() {
        return clave;
    }

    /** Con qué clave se copia al detalle del paso {@code ENVIADA} (id de la media y ruta). */
    public String claveMediaEnvio() {
        return claveMediaEnvio;
    }

    public String claveRutaEnvio() {
        return claveRutaEnvio;
    }

    public static String prefijoDe(UserId aprendizId) {
        return "onboarding/" + aprendizId + "/caja/";
    }

    public static String rutaNueva(UserId aprendizId, UUID identificadorSubida) {
        return prefijoDe(aprendizId) + identificadorSubida;
    }

    /** @throws IllegalArgumentException si no es JPEG ni PNG (400) */
    public static String exigirTipoDeContenido(String tipoContenido) {
        String tipo = tipoContenido == null || tipoContenido.isBlank() ? "image/jpeg"
                : tipoContenido.strip().toLowerCase(Locale.ROOT);
        if (!TIPOS_DE_CONTENIDO.contains(tipo)) {
            throw new IllegalArgumentException("La foto tiene que ser JPG o PNG.");
        }
        return tipo;
    }

    /**
     * La ruta tiene que ser una que emitió el servidor para la caja de ESE aprendiz: nunca la de otro objeto
     * del depósito (sus audios, otra caja).
     *
     * @throws IllegalArgumentException si apunta fuera de su caja (400)
     */
    public static String exigirRutaDe(UserId aprendizId, String ruta) {
        String prefijo = prefijoDe(aprendizId);
        boolean propia = ruta != null && ruta.startsWith(prefijo) && ruta.length() > prefijo.length()
                && ruta.length() <= LARGO_MAXIMO_DE_RUTA && !ruta.contains("..") && !ruta.contains("//");
        if (!propia) {
            throw new IllegalArgumentException("La foto tiene que ser una subida para esta caja.");
        }
        return ruta;
    }
}
