package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Hasta dónde leyeron TODOS en una conversación: de ahí sale el ✓✓ de cada mensaje propio (D-208).
 *
 * <p><b>Es una marca de agua, no una lectura por mensaje.</b> Cada participante tiene su
 * {@code ultimo_leido_en} (lo mueve al abrir el chat y al escribir). El mínimo entre los que pueden
 * leer es el instante hasta el cual todos leyeron: un mensaje escrito en ese instante o antes está
 * leído. Se calcula con una sola lectura de los participantes por conversación, nunca por mensaje, y
 * no hace falta una tabla nueva.
 *
 * <p><b>Por qué el mínimo entre TODOS sirve para los mensajes de cualquiera.</b> La pregunta de
 * WhatsApp es «¿lo leyeron todos los DEMÁS?», que depende de quién escribió. Pero quien escribe queda
 * marcado como leído en el mismo instante en que guarda su mensaje ({@code MensajeService.enviar}), y
 * su marca solo avanza: nunca está antes de lo que escribió. Así que para un mensaje suyo, el mínimo
 * entre todos y el mínimo entre los demás dicen lo mismo. Por eso la misma marca vale para cada
 * persona que mira y puede viajar entera en el aviso en vivo, sin armar uno distinto por destinatario.
 *
 * <p>Sin doble marca, y sin excepción:
 * <ul>
 *   <li>en la comunidad (GLOBAL, ver {@code TipoConversacion.confirmaLectura});</li>
 *   <li>con menos de dos que puedan leer: si del otro lado no queda nadie, nadie lo leyó;</li>
 *   <li>si alguno nunca abrió la conversación ({@code ultimo_leido_en} nulo): ese no leyó nada.</li>
 * </ul>
 *
 * <p>Quiénes «pueden leer» los decide quien llama: los participantes con la cuenta activa. Uno
 * suspendido no puede abrir el chat, y contarlo dejaría a su grupo o a su soporte sin ✓✓ para siempre.
 */
public final class ConfirmacionDeLectura {

    private static final ConfirmacionDeLectura SIN_DOBLE_MARCA = new ConfirmacionDeLectura(null);

    /** {@code null}: ningún mensaje lleva ✓✓ en esta conversación. */
    private final Instant leidoPorTodosHasta;

    private ConfirmacionDeLectura(Instant leidoPorTodosHasta) {
        this.leidoPorTodosHasta = leidoPorTodosHasta;
    }

    /** La de la comunidad, o la de una conversación que todavía nadie leyó entera. */
    public static ConfirmacionDeLectura sinDobleMarca() {
        return SIN_DOBLE_MARCA;
    }

    /**
     * @param quienesPuedenLeer los participantes de {@code conversacion} que hoy pueden abrirla (cuenta
     *                          activa), con su marca de lectura
     */
    public static ConfirmacionDeLectura de(Conversacion conversacion, Collection<Participante> quienesPuedenLeer) {
        Objects.requireNonNull(conversacion, "conversacion es obligatoria");
        Objects.requireNonNull(quienesPuedenLeer, "quienesPuedenLeer es obligatorio");
        if (!conversacion.confirmaLectura() || quienesPuedenLeer.size() < 2) {
            return SIN_DOBLE_MARCA;
        }
        Instant minima = Instant.MAX;
        for (Participante participante : quienesPuedenLeer) {
            Instant marca = participante.ultimoLeidoEn();
            if (marca == null) {
                return SIN_DOBLE_MARCA;
            }
            minima = marca.isBefore(minima) ? marca : minima;
        }
        return new ConfirmacionDeLectura(minima);
    }

    /** Hasta qué instante leyeron todos. Vacío si ningún mensaje lleva ✓✓. */
    public Optional<Instant> leidoPorTodosHasta() {
        return Optional.ofNullable(leidoPorTodosHasta);
    }

    /**
     * La marca de {@code mensaje} para quien lo mira. Vacía si no es suyo (el de otra persona o uno
     * del programa): en el chat solo los propios llevan ✓ o ✓✓.
     */
    public Optional<EstadoDeEntrega> estadoPara(Mensaje mensaje, UserId quienMira) {
        if (!mensaje.escritoPor(quienMira)) {
            return Optional.empty();
        }
        boolean leido = leidoPorTodosHasta != null && !mensaje.creadoEn().isAfter(leidoPorTodosHasta);
        return Optional.of(leido ? EstadoDeEntrega.LEIDO : EstadoDeEntrega.ENVIADO);
    }

    @Override
    public String toString() {
        return "ConfirmacionDeLectura[" + leidoPorTodosHasta + "]";
    }
}
