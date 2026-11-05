package com.renaser.os.notifications.domain.model.evento;

import java.time.Duration;
import java.time.Instant;

/**
 * Si un aviso de mensaje todavia sirve (spec 041, FR-016), con las dos reglas que se copiaron de RenaserOs. <b>Sin horario</b>: sale a cualquier hora,
 * tambien de madrugada.
 * <ul>
 *   <li>Una ocurrencia que <b>ya empezo</b> no recibe su aviso: diria «empieza en...» de algo que paso.</li>
 *   <li>Lo que llega con <b>mas de 15 minutos de atraso</b>, contados desde la hora del aviso (tambien al cruzar la medianoche), no sale ni se difiere: el dia
 *       que se enciende el canal o tras una caida del servidor no se manda lo viejo. Mejor no avisar que avisar tarde.</li>
 * </ul>
 */
public enum VeredictoDelMensaje {

    ENVIAR(""),
    EL_EVENTO_YA_EMPEZO("El evento ya empezó"),
    LLEGO_TARDE("Llegó tarde");

    /** Hasta cuanto despues de su hora todavia sale un aviso de mensaje. */
    public static final Duration ATRASO_MAXIMO = Duration.ofMinutes(15);

    private final String motivo;

    VeredictoDelMensaje(String motivo) {
        this.motivo = motivo;
    }

    /** El motivo que queda anotado cuando no se envia. */
    public String motivo() {
        return motivo;
    }

    public boolean envia() {
        return this == ENVIAR;
    }

    /**
     * @param inicio       el inicio de la ocurrencia
     * @param minutosAntes cuantos minutos antes del inicio es este aviso (1440, 60, 30, 10)
     */
    public static VeredictoDelMensaje de(Instant inicio, int minutosAntes, Instant ahora, Duration atrasoMaximo) {
        if (!ahora.isBefore(inicio)) {
            return EL_EVENTO_YA_EMPEZO;
        }
        Instant suHora = inicio.minus(Duration.ofMinutes(minutosAntes));
        return ahora.isAfter(suHora.plus(atrasoMaximo)) ? LLEGO_TARDE : ENVIAR;
    }
}
