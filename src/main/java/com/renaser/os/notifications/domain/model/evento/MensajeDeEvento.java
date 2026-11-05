package com.renaser.os.notifications.domain.model.evento;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * El recordatorio de un evento como MENSAJE (spec 041, FR-001 y FR-017): lo que se escribe en el chat de soporte y los cinco huecos de la
 * plantilla de WhatsApp. Sale de {@link AvisoDeEvento}, el mismo texto que ve la app, para que haya un solo lugar donde se diga «cuando» empieza.
 *
 * @param enlace el enlace para entrar si el evento es en linea (Meet, Zoom, otro); {@code null} si no hay
 * @param lugar  la direccion si el evento es presencial; {@code null} si no
 */
public record MensajeDeEvento(AvisoDeEvento aviso, String enlace, String lugar) {

    /** La linea final de la plantilla cuando el evento no trae enlace ni direccion. */
    static final String MIRALO_EN_RENASER = "Míralo en Renaser.";
    private static final String PREFIJO_ENLACE = "Entra aquí: ";
    private static final String PREFIJO_LUGAR = "Lugar: ";

    public MensajeDeEvento {
        Objects.requireNonNull(aviso, "aviso es obligatorio");
    }

    /** El titulo, cuando empieza y, si hay, el enlace o el lugar: tres lineas como mucho. */
    public String textoParaElChat(Instant ahora) {
        StringBuilder texto = new StringBuilder(aviso.titulo()).append('\n')
                .append(mayuscula(aviso.cuandoEnElMensaje(ahora))).append('.');
        dondeEs().ifPresent(donde -> texto.append('\n').append(donde));
        return texto.toString();
    }

    /**
     * Los cinco huecos de la plantilla: nombre de pila, nombre de la formacion, titulo, cuando y enlace o lugar. Cada hueco se limpia
     * ({@link HuecoDePlantilla}) porque Meta rechaza saltos de linea y mas de cuatro espacios seguidos.
     */
    public List<String> huecosDeWhatsApp(String nombreDePila, String nombreDeLaFormacion, Instant ahora) {
        return List.of(HuecoDePlantilla.limpiar(nombreDePila), HuecoDePlantilla.limpiar(nombreDeLaFormacion),
                HuecoDePlantilla.limpiar(aviso.tituloEvento()), HuecoDePlantilla.limpiar(aviso.cuandoEnElMensaje(ahora)),
                HuecoDePlantilla.limpiar(dondeEs().orElse(MIRALO_EN_RENASER)));
    }

    private java.util.Optional<String> dondeEs() {
        if (enlace != null && !enlace.isBlank()) {
            return java.util.Optional.of(PREFIJO_ENLACE + enlace.strip());
        }
        if (lugar != null && !lugar.isBlank()) {
            return java.util.Optional.of(PREFIJO_LUGAR + lugar.strip());
        }
        return java.util.Optional.empty();
    }

    private static String mayuscula(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
