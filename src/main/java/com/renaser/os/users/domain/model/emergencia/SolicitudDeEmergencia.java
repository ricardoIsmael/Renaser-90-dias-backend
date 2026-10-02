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
 *   <li>A qué día: entre 1 y el día que vive hoy. El tope es {@value ParticipacionPrograma#ULTIMO_DIA_AJUSTABLE}
 *       aunque esté en el 90, porque el 90 no se fija a mano (D-194).</li>
 *   <li>Antes del Día 1 no hay a qué día volver: {@link IllegalStateException} (409).</li>
 * </ul>
 * Que haya una sola abierta por persona depende de otras filas: lo revisa el caso de uso y lo cierra el
 * índice único parcial de la tabla.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class SolicitudDeEmergencia {

    public static final int LARGO_MAXIMO = 280;
    public static final int PRIMER_DIA = ParticipacionPrograma.PRIMER_DIA_AJUSTABLE;
    public static final String ANTES_DEL_DIA_UNO = "Tu programa todavía no empezó: no hay un día al que volver.";

    private final UUID id;
    private final UserId aprendizId;
    private final String queOcurrio;
    private final int diaPedido;
    private final int diaAlPedir;
    private EstadoDeEmergencia estado;
    private final Instant creadaEn;
    private Instant resueltaEn;
    private UserId resueltaPor;
    private Integer diaAplicado;

    /**
     * @param diaActual el día que vive hoy en su zona ({@code ParticipacionPrograma.diaVigente})
     * @throws IllegalArgumentException si falta el texto, es muy largo o el día está fuera de rango (400)
     * @throws IllegalStateException si todavía no empezó su Día 1 (409)
     */
    public static SolicitudDeEmergencia pedir(UUID id, UserId aprendizId, String queOcurrio, int diaPedido,
                                              int diaActual, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        String texto = requireTexto(queOcurrio);
        if (diaActual < PRIMER_DIA) {
            throw new IllegalStateException(ANTES_DEL_DIA_UNO);
        }
        int maximo = diaMaximoPedible(diaActual);
        if (diaPedido < PRIMER_DIA || diaPedido > maximo) {
            throw new IllegalArgumentException("Elige un día entre " + PRIMER_DIA + " y " + maximo + ".");
        }
        return new SolicitudDeEmergencia(id, aprendizId, texto, diaPedido, diaActual, EstadoDeEmergencia.ABIERTA,
                clock.now(), null, null, null);
    }

    /** Solo para el adaptador de persistencia. */
    public static SolicitudDeEmergencia rehydrate(UUID id, UserId aprendizId, String queOcurrio, int diaPedido,
                                                  int diaAlPedir, EstadoDeEmergencia estado, Instant creadaEn,
                                                  Instant resueltaEn, UserId resueltaPor, Integer diaAplicado) {
        return new SolicitudDeEmergencia(id, aprendizId, queOcurrio, diaPedido, diaAlPedir, estado, creadaEn,
                resueltaEn, resueltaPor, diaAplicado);
    }

    /** Hasta qué día puede pedir quien vive hoy {@code diaActual}; 0 si todavía no empezó. */
    public static int diaMaximoPedible(int diaActual) {
        if (diaActual < PRIMER_DIA) {
            return 0;
        }
        return Math.min(diaActual, ParticipacionPrograma.ULTIMO_DIA_AJUSTABLE);
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
