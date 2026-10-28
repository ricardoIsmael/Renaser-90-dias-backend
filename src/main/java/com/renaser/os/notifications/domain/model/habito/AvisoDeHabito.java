package com.renaser.os.notifications.domain.model.habito;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * El aviso que recibe una persona por un habito que esta por empezar o por vencer (pedido del dueno,
 * 2026-09-05): que dice, con que etiqueta se reemplaza en el telefono y cuando ya no tiene sentido
 * mandarlo. Dominio puro: el instante actual entra como argumento. Hermano de
 * {@link com.renaser.os.notifications.domain.model.evento.AvisoDeEvento}.
 *
 * <p><b>Dice la hora del habito, no una cuenta de minutos</b> (spec 007, FR-012). El texto viejo
 * («empieza en 12 min») salia del instante del barrido, que corre cada 5 minutos: con la alarma del
 * telefono sonando 1 minuto antes, el servidor decia «2 min» y no coincidian. Ahora, con el momento
 * exacto en la zona del participante, dice «te toca a las 07:00», que es verdad llegue cuando llegue. La
 * hora se formatea en la zona del programa, no en UTC (regla 02 §1): las 21:00 de Lima son las 02:00 UTC.
 *
 * <p>Un evento publicado ANTES de la spec 007 que el outbox reentregue no trae momento ni zona: se
 * redacta con los minutos de siempre (ya con tildes, FR-016), sin inventar una hora.
 *
 * @param tipoAviso     {@code INICIO} o {@code POR_VENCER}, como String por el mismo motivo que en
 *                      {@link EntregaDelAvisoDeHabito}: el tipo interno de `habits` no cruza su
 *                      {@code @NamedInterface}
 * @param tituloHabito  el titulo del habito tal como esta en el catalogo
 * @param minutosQueFaltan lo que faltaba al calcularse, redondeado hacia arriba; solo sirve para el texto
 *                      de los eventos viejos y para reconstruir su momento
 * @param puntosEnJuego lo que gana quien lo entrega ahora, calculado por `habits` (D-97)
 * @param habitoId      el habito (no el registro del dia); {@code null} en eventos anteriores a D-218
 * @param emitidoEn     cuando `habits` calculo el aviso ({@code occurredAt} del evento)
 * @param momentoAvisado la hora de disparo (inicio) o el plazo de entrega (vencimiento), o {@code null}
 *                      en un evento anterior a la spec 007
 * @param zonaHoraria   la zona en que se dice esa hora ({@code America/Lima}), o {@code null} igual que arriba
 * @param diaDeLaSemanaDelAviso el dia de la semana ISO (1 = lunes) del aviso cuando el habito usa el esquema por dia
 *                      (spec 008, R6); {@code null} si usa el diario o en un evento anterior a la spec 008
 */
public record AvisoDeHabito(String tipoAviso, String tituloHabito, long minutosQueFaltan, int puntosEnJuego,
                            UUID habitoId, Instant emitidoEn, Instant momentoAvisado, String zonaHoraria,
                            Integer diaDeLaSemanaDelAviso) {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ESPANOL);

    public AvisoDeHabito {
        Objects.requireNonNull(tipoAviso, "tipoAviso es obligatorio");
        Objects.requireNonNull(tituloHabito, "tituloHabito es obligatorio");
        Objects.requireNonNull(emitidoEn, "emitidoEn es obligatorio");
    }

    public String titulo() {
        return esInicio() ? "Tu hábito está por empezar" : "Se te vence un hábito";
    }

    /**
     * Los puntos en juego van en el texto por pedido explicito del dueno («al mencionar un habito, decir
     * cuantos puntos se ganan o se pierden»). El numero lo calcula `habits` y este modulo no lo recalcula.
     */
    public String cuerpo() {
        return tituloHabito + cuando() + ". En juego: " + puntosEnJuego + " puntos.";
    }

    /**
     * Con ella el telefono reemplaza —en vez de apilar— el aviso del servidor y la alarma local del mismo
     * habito y momento (FR-014): la app programa la antelacion mas temprana con este mismo identificador.
     * El aviso de vencimiento tiene la suya para no reemplazar al de inicio. {@code null} sin habito
     * (evento anterior a D-218): cada aviso es uno aparte, como hasta ahora.
     *
     * <p><b>Spec 008 (R6).</b> Si el habito usa el esquema por dia, la app nombra la alarma mas temprana de
     * cada dia {@code habito-{id}-d{iso}-0}, y el push de INICIO de ese dia lleva la misma. Sin dia (esquema
     * diario) sigue siendo {@code habito-{id}-inicio}. El de vencimiento no cambia: la app no programa
     * ninguna alarma para el plazo de entrega.
     */
    public String etiqueta() {
        if (habitoId == null) {
            return null;
        }
        if (!esInicio()) {
            return "habito-" + habitoId + "-por-vencer";
        }
        return "habito-" + habitoId + (diaDeLaSemanaDelAviso == null ? "-inicio" : "-d" + diaDeLaSemanaDelAviso + "-0");
    }

    /**
     * Un aviso que llega cuando su momento ya paso (backend caido, outbox que reintenta tarde) no se manda:
     * diria «te toca a las 07:00» de algo que ya empezo. Es preferible no avisar a avisar algo falso (mismo
     * criterio que {@code AvisoDeEvento.yaNoSirve} y que la franja de {@code CalculadoraAvisosHabito}).
     */
    public boolean yaNoSirve(Instant ahora) {
        return !ahora.isBefore(venceEn());
    }

    /**
     * El instante desde el cual este aviso ya no sirve: la hora del habito (inicio) o el plazo de entrega
     * (por vencer). Es el mismo que usa {@link #yaNoSirve}, expuesto para que el push lo lleve como caducidad
     * (E-645, issue #22): sin ella el proveedor (FCM) retiene el mensaje hasta 4 semanas y lo entrega tarde,
     * cuando el servidor ya sabia que no habia que mandarlo. Una sola fuente: lo que el servidor descarta y lo
     * que el proveedor descarta no pueden diferir.
     *
     * <p>El momento del evento si viene; si no (evento viejo), el de la emision mas lo que faltaba, truncado
     * al minuto: las horas de los habitos son {@code HH:mm} exactas, y los segundos de la emision no son
     * parte de la hora del habito.
     */
    public Instant venceEn() {
        if (momentoAvisado != null) {
            return momentoAvisado;
        }
        return emitidoEn.plus(minutosQueFaltan, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    }

    private String cuando() {
        if (momentoAvisado == null || zonaHoraria == null) {
            return esInicio() ? " empieza en " + minutosQueFaltan + " min" : " vence en " + minutosQueFaltan + " min";
        }
        String hora = HORA.format(momentoAvisado.atZone(ZoneId.of(zonaHoraria)));
        return esInicio() ? ": te toca a las " + hora : " vence a las " + hora;
    }

    private boolean esInicio() {
        return EntregaDelAvisoDeHabito.AVISO_INICIO.equals(tipoAviso);
    }
}
