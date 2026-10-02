package com.renaser.os.users.domain.model.emergencia;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un aprendiz pide volver a un día del programa porque tuvo una emergencia (D-244, pedido del dueño del
 * 2026-10-02). <b>No mueve el reloj</b>: es un pedido que le llega a soporte, y quien atiende lo aplica
 * con «Cambiar día del programa» ({@code ParticipacionPrograma.fijarDia}), o lo cierra sin cambiarlo.
 *
 * <p>Reglas que se evalúan acá, con los datos del pedido:
 * <ul>
 *   <li>Qué pasó: obligatorio, hasta {@value #LARGO_MAXIMO} caracteres (el tope del motivo de un ajuste de
 *       día, para que quien atiende pueda pasarlo entero).</li>
 *   <li>A qué día: entre 1 y el día que vive hoy, incluido hoy. El tope es
 *       {@value ParticipacionPrograma#ULTIMO_DIA_AJUSTABLE} aunque esté en el 90, porque el 90 no se fija a mano
 *       (D-194).</li>
 *   <li><b>En el Día 0 también se puede pedir</b> (decisión del dueño del 2026-10-02): es solo «necesito
 *       ayuda», sin día ({@code diaPedido} vacío), porque no hay un día anterior al que volver.</li>
 * </ul>
 * <blockquote><b>Corregido 2026-10-02 (mismo día, respuesta del dueño).</b> Decía «Antes del Día 1 no hay a qué
 * día volver: {@link IllegalStateException} (409)». El dueño pidió el botón desde el Día 0.</blockquote>
 *  * Que haya una sola abierta por persona depende de otras filas: lo revisa el caso de uso y lo cierra el
 * índice único parcial de la tabla.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class SolicitudDeEmergencia {

    public static final int LARGO_MAXIMO = 280;
    public static final int PRIMER_DIA = ParticipacionPrograma.PRIMER_DIA_AJUSTABLE;
    public static final String SIN_DIA_EN_EL_DIA_CERO = "Todavía estás en el día 0: no hace falta elegir un día.";

    private final UUID id;
    private final UserId aprendizId;
    private final String queOcurrio;
    /** {@code null} si lo pidió en el Día 0: es solo un pedido de ayuda. */
    private final Integer diaPedido;
    private final int diaAlPedir;
    private EstadoDeEmergencia estado;
    private final Instant creadaEn;
    private Instant resueltaEn;
    private UserId resueltaPor;
    private Integer diaAplicado;

    /**
     * @param diaPedido a qué día quiere volver; {@code null} (y solo así) si todavía está en el Día 0
     * @param diaActual el día que vive hoy en su zona ({@code ParticipacionPrograma.diaVigente})
     * @throws IllegalArgumentException si falta el texto, es muy largo o el día no corresponde (400)
     */
    public static SolicitudDeEmergencia pedir(UUID id, UserId aprendizId, String queOcurrio, Integer diaPedido,
                                              int diaActual, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        String texto = requireTexto(queOcurrio);
        int dia = Math.max(0, diaActual);
        requireDiaQueCorresponde(diaPedido, dia);
        return new SolicitudDeEmergencia(id, aprendizId, texto, diaPedido, dia, EstadoDeEmergencia.ABIERTA,
                clock.now(), null, null, null);
    }

    private static void requireDiaQueCorresponde(Integer diaPedido, int diaActual) {
        int maximo = diaMaximoPedible(diaActual);
        if (maximo == 0) {
            if (diaPedido != null) {
                throw new IllegalArgumentException(SIN_DIA_EN_EL_DIA_CERO);
            }
            return;
        }
        if (diaPedido == null || diaPedido < PRIMER_DIA || diaPedido > maximo) {
            throw new IllegalArgumentException("Elige un día entre " + PRIMER_DIA + " y " + maximo + ".");
        }
    }

    /** Solo para el adaptador de persistencia. */
    public static SolicitudDeEmergencia rehydrate(UUID id, UserId aprendizId, String queOcurrio, Integer diaPedido,
                                                  int diaAlPedir, EstadoDeEmergencia estado, Instant creadaEn,
                                                  Instant resueltaEn, UserId resueltaPor, Integer diaAplicado) {
        return new SolicitudDeEmergencia(id, aprendizId, queOcurrio, diaPedido, diaAlPedir, estado, creadaEn,
                resueltaEn, resueltaPor, diaAplicado);
    }

    /** Hasta qué día puede pedir quien vive hoy {@code diaActual}; 0 en el Día 0 (pide ayuda sin día). */
    public static int diaMaximoPedible(int diaActual) {
        if (diaActual < PRIMER_DIA) {
            return 0;
        }
        return Math.min(diaActual, ParticipacionPrograma.ULTIMO_DIA_AJUSTABLE);
    }

    /** Si pidió volver a un día (no es un pedido del Día 0). */
    public boolean pideUnDia() {
        return diaPedido != null;
    }

    public boolean abierta() {
        return estado == EstadoDeEmergencia.ABIERTA;
    }

    /** Quien atiende cambió el día de la persona (a cualquier día: puede no ser el pedido). Una vez. */
    public void resolverConCambioDeDia(UserId quien, int diaNuevo, Clock clock) {
        cerrar(quien, diaNuevo, clock);
    }

    /** Quien atiende la cierra sin mover el día (lo habló con la persona, o no corresponde). Una vez. */
    public void cerrarSinCambio(UserId quien, Clock clock) {
        cerrar(quien, null, clock);
    }

    private void cerrar(UserId quien, Integer dia, Clock clock) {
        Objects.requireNonNull(quien, "quien la cierra es obligatorio");
        if (!abierta()) {
            throw new IllegalStateException("Este pedido ya estaba resuelto.");
        }
        this.estado = EstadoDeEmergencia.RESUELTA;
        this.resueltaPor = quien;
        this.diaAplicado = dia;
        this.resueltaEn = clock.now();
    }

    private static String requireTexto(String queOcurrio) {
        String texto = queOcurrio == null ? "" : queOcurrio.strip();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Cuéntanos en pocas palabras qué pasó.");
        }
        if (texto.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("Escríbelo en " + LARGO_MAXIMO + " caracteres o menos.");
        }
        return texto;
    }

    @Override
    public String toString() {
        return "SolicitudDeEmergencia[" + id + ", " + aprendizId + ", dia " + diaPedido + " desde " + diaAlPedir
                + ", " + estado + "]";
    }
}
