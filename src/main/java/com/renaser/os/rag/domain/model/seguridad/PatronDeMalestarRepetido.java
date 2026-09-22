package com.renaser.os.rag.domain.model.seguridad;

import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * "Esta persona escribio varias veces, en poco tiempo, expresiones de {@link ExpresionesDeMalestar}."
 * Nada mas que eso: es la constatacion de una repeticion, no una afirmacion sobre como esta nadie.
 *
 * <h2>Los dos numeros del umbral, en UN solo lugar</h2>
 * {@link #DETECCIONES_PARA_AVISAR} y {@link #VENTANA} son los unicos valores del mecanismo y no se
 * repiten en ninguna otra clase: el caso de uso, el aviso y las pruebas los leen de aca.
 * <b>Los dos estan a confirmar con el dueno del producto.</b> Tres repeticiones en siete dias es
 * lo que se fijo para arrancar; no salio de ninguna regla clinica ni de ningun dato del programa.
 * Cambiarlos es cambiar estas dos lineas.
 *
 * <h2>Se deriva de las fechas, no se acumula (regla 02 §2)</h2>
 * No hay contador ni tabla de detecciones: la cuenta sale cada vez de los mensajes que la persona
 * ya tiene guardados en {@code mensajes_renasia}, que es donde viven su texto y su fecha. De ahi
 * salen tres propiedades gratis: correrlo dos veces da lo mismo, una noche con el backend caido no
 * pierde nada, y las detecciones viejas salen solas de la ventana sin que nadie las borre.
 *
 * <h2>El "episodio", y por que la clave de deduplicacion es la primera deteccion</h2>
 * {@link #inicioDelPatron} es el instante de la deteccion mas vieja que todavia esta dentro de la
 * ventana. Mientras la condicion siga dandose, ese instante no se mueve —un mensaje nuevo nunca
 * puede ser mas viejo que el primero—, asi que la clave tampoco se mueve y el administrador recibe
 * UN aviso por episodio. Recien cuando esa primera deteccion sale de la ventana y la condicion
 * vuelve a darse con otras, el episodio es otro y corresponde otro aviso. Mismo criterio que
 * {@code AvisoDeAcompanamiento.claveDeDeduplicacion} en {@code mentoring}.
 *
 * @param detecciones    cuantas expresiones se contaron. Es el numero del MOMENTO en que se emitio
 *                       el aviso: como la clave no cambia, el aviso no se reescribe si despues
 *                       suben a cuatro o cinco. Para eso esta la palabra "al menos" en el texto.
 * @param inicioDelPatron instante del mensaje mas viejo que se conto. Ver arriba.
 */
public record PatronDeMalestarRepetido(UserId usuarioId, int detecciones, Instant inicioDelPatron) {

    /**
     * Cuantas veces tiene que repetirse antes de que pase algo. <b>A confirmar con el dueno del
     * producto.</b> Es deliberadamente mayor que uno: una sola frase mala un martes es la molestia
     * corriente que produce cualquier programa exigente ({@link Severidad#BAJA}), y responderle a
     * eso con un recurso de ayuda hace que la persona deje de escribirle al asistente.
     */
    public static final int DETECCIONES_PARA_AVISAR = 3;

    /**
     * Cuanto hacia atras se mira. <b>A confirmar con el dueno del producto.</b> Siete dias porque
     * es la unidad con la que el programa ya razona (el plan es semanal, el informe del Espejo es
     * semanal); no hay otra razon.
     */
    public static final Duration VENTANA = Duration.ofDays(7);

    public PatronDeMalestarRepetido {
        if (usuarioId == null) {
            throw new IllegalArgumentException("usuarioId es obligatorio");
        }
        if (detecciones < DETECCIONES_PARA_AVISAR) {
            throw new IllegalArgumentException(
                    "No hay patron repetido con menos de " + DETECCIONES_PARA_AVISAR + " detecciones");
        }
        if (inicioDelPatron == null) {
            throw new IllegalArgumentException("inicioDelPatron es obligatorio");
        }
    }

    /** Desde cuando hay que leer los mensajes para poder evaluar la regla en {@code ahora}. */
    public static Instant inicioDeLaVentana(Instant ahora) {
        return ahora.minus(VENTANA);
    }

    /**
     * Cuenta las detecciones de {@code mensajes} que caen dentro de la ventana que termina en
     * {@code ahora} y devuelve el patron solo si llegan al umbral.
     *
     * <p>Filtra {@link RolMensaje#USUARIO} y la ventana por su cuenta aunque el puerto ya lo haga:
     * la regla tiene que poder probarse —y ser correcta— sin depender de que el llamador se porte
     * bien. Un mensaje del ASISTENTE nunca cuenta, ni siquiera si el asistente repitio la frase de
     * la persona al responderle.
     */
    public static Optional<PatronDeMalestarRepetido> enLaVentana(UserId usuarioId, List<MensajeRenasia> mensajes,
                                                                  Instant ahora) {
        Instant desde = inicioDeLaVentana(ahora);
        List<Instant> detecciones = mensajes.stream()
                .filter(mensaje -> mensaje.rol() == RolMensaje.USUARIO)
                .filter(mensaje -> !mensaje.creadoEn().isBefore(desde))
                .filter(mensaje -> ExpresionesDeMalestar.apareceEn(mensaje.contenido()))
                .map(MensajeRenasia::creadoEn)
                .sorted()
                .toList();
        if (detecciones.size() < DETECCIONES_PARA_AVISAR) {
            return Optional.empty();
        }
        return Optional.of(new PatronDeMalestarRepetido(usuarioId, detecciones.size(), detecciones.getFirst()));
    }

    /**
     * Clave estable del episodio. Viaja como {@code origenEventoId}, que tiene indice unico en
     * {@code notificaciones} (V16): revisar el patron en cada mensaje no multiplica el aviso, y la
     * reentrega del outbox de Modulith —que es at-least-once— tampoco.
     */
    public UUID claveDeDeduplicacion() {
        String semilla = usuarioId.value() + "|malestar-repetido|" + inicioDelPatron;
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }

    /** Los dias de {@link #VENTANA}, para el texto del aviso: un solo lugar define el numero. */
    public static int diasDeLaVentana() {
        return (int) VENTANA.toDays();
    }
}
