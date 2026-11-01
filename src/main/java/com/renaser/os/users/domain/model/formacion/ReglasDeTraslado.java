package com.renaser.os.users.domain.model.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.Optional;
import java.util.Set;

/**
 * Cuándo un aprendiz puede pasar de una formación a otra (spec 022, Q1-A, decisiones del dueño del 31/10).
 *
 * <ul>
 *   <li>Solo <b>cuando terminó</b> su formación. Antes, Administración la da por terminada con un <b>motivo escrito</b> (queda en el historial).</li>
 *   <li>Quien <b>todavía no empezó</b> (no eligió su Día 1) cambia de formación o de duración sin dejar historia (FR-030): nunca empezó nada.</li>
 *   <li>El destino es una formación distinta y activa, y ofrece la duración pedida. Una misma formación sí admite cambiar de duración si la persona
 *       todavía no empezó.</li>
 * </ul>
 *
 * Es lógica pura, sin Spring ni base de datos: recibe lo que necesita ya resuelto.
 */
public final class ReglasDeTraslado {

    public static final String FALTA_EL_MOTIVO = "Escribe el motivo para dar por terminada su formación (de 3 a 500 letras).";
    public static final String TODAVIA_NO_TERMINO =
            "Esta persona todavía no terminó su formación. Para pasarla de formación, dala por terminada con un motivo escrito.";
    public static final String MISMA_FORMACION = "La persona ya está en esa formación.";
    private static final int MOTIVO_MINIMO = 3;
    private static final int MOTIVO_MAXIMO = 500;

    private ReglasDeTraslado() {
    }

    /** Lo que decide el traslado: si deja historia o es una simple corrección. */
    public enum Tipo {
        /** No había empezado (sin Día 1 elegido): se corrige sin dejar historia. */
        CORRECCION_SIN_HISTORIA,
        /** Había empezado: deja una participación anterior con este resultado. */
        CON_HISTORIA
    }

    public record Decision(Tipo tipo, Optional<ResultadoDelTraslado> resultado, String motivo) {

        public static Decision correccion() {
            return new Decision(Tipo.CORRECCION_SIN_HISTORIA, Optional.empty(), null);
        }

        public static Decision conHistoria(ResultadoDelTraslado resultado, String motivo) {
            return new Decision(Tipo.CON_HISTORIA, Optional.of(resultado), motivo);
        }
    }

    /**
     * @param duracionesDelDestino las duraciones que ofrece la formación de destino
     * @param destinoActiva        si la formación de destino se ofrece a personas nuevas
     * @throws IllegalArgumentException (400) si el motivo no sirve o el destino no ofrece esa duración
     * @throws IllegalStateException    (409) si todavía no terminó y no se la da por terminada, o si ya está en esa formación y duración
     */
    public static Decision decidir(ParticipacionPrograma actual, Formacion destino, int duracion, boolean darPorTerminada,
                                   String motivo, Set<Integer> duracionesDelDestino, boolean destinoActiva) {
        if (!destinoActiva) {
            throw new IllegalArgumentException("Esa formación no está disponible por ahora.");
        }
        if (!duracionesDelDestino.contains(duracion)) {
            throw new IllegalArgumentException("Esa formación no ofrece una duración de " + duracion + " días.");
        }
        if (!actual.estaActivado()) {
            return Decision.correccion();
        }
        if (actual.formacion() == destino) {
            throw new IllegalStateException(MISMA_FORMACION);
        }
        if (actual.programaCompletado()) {
            return Decision.conHistoria(ResultadoDelTraslado.TERMINADA, null);
        }
        if (!darPorTerminada) {
            throw new IllegalStateException(TODAVIA_NO_TERMINO);
        }
        return Decision.conHistoria(ResultadoDelTraslado.DADA_POR_TERMINADA, motivoValido(motivo));
    }

    private static String motivoValido(String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.length() < MOTIVO_MINIMO || limpio.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException(FALTA_EL_MOTIVO);
        }
        return limpio;
    }
}
