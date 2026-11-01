package com.renaser.os.points.domain.model.puntaje;

import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "participanteId")
public final class PuntajeParticipante {

    public static final int PUNTOS_LIGA_INICIAL = 100;
    public static final BigDecimal COHERENCIA_INICIAL = BigDecimal.valueOf(100);

    public static final int RACHA_BONO_CADA_DIAS = 3;
    public static final int RACHA_BONO_PUNTOS = 5;

    private final UserId participanteId;
    private BigDecimal coherencia;
    private int puntosLiga;
    private int rachaActual;
    private int rachaMaxima;
    private Instant actualizadoEn;

    public static PuntajeParticipante inicial(UserId participanteId, Clock clock) {
        return new PuntajeParticipante(requireId(participanteId), COHERENCIA_INICIAL, PUNTOS_LIGA_INICIAL, 0, 0,
                clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static PuntajeParticipante rehydrate(UserId participanteId, BigDecimal coherencia, int puntosLiga,
                                                 int rachaActual, int rachaMaxima, Instant actualizadoEn) {
        return new PuntajeParticipante(participanteId, coherencia, puntosLiga, rachaActual, rachaMaxima,
                actualizadoEn);
    }

    public ResultadoAjuste registrarAjuste(int deltaSolicitado, Clock clock) {
        int saldoAnterior = this.puntosLiga;
        int saldoPosterior = Math.max(saldoAnterior + deltaSolicitado, 0);
        int deltaAplicado = saldoPosterior - saldoAnterior;

        this.puntosLiga = saldoPosterior;
        this.actualizadoEn = clock.now();

        return new ResultadoAjuste(deltaSolicitado, deltaAplicado, saldoAnterior, saldoPosterior);
    }

    public void actualizarCoherencia(BigDecimal valor, Clock clock) {
        Objects.requireNonNull(valor, "valor de coherencia es obligatorio");
        if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La coherencia debe estar entre 0 y 100: " + valor);
        }
        this.coherencia = valor;
        this.actualizadoEn = clock.now();
    }

    public boolean actualizarRachaTrasDia(boolean diaHabitosPerfecto, Clock clock) {
        int nuevaRacha = diaHabitosPerfecto ? this.rachaActual + 1 : 0;
        this.rachaMaxima = Math.max(this.rachaMaxima, nuevaRacha);
        this.rachaActual = nuevaRacha;
        this.actualizadoEn = clock.now();
        return diaHabitosPerfecto && correspondeBonoDeRacha(nuevaRacha);
    }

    /**
     * Empieza de cero en otra formación (spec 022, Q8-A): la coherencia, la racha y su máximo vuelven al inicio. Los puntos de la liga NO se
     * borran aquí: se reinician con un ajuste del ledger ({@link #registrarAjuste}), que deja a la vista de dónde venían — el ledger es append-only.
     * Devuelve el ajuste que lleva el saldo al inicial, o 0 si ya estaba ahí.
     */
    public int reiniciarParaOtraFormacion(Clock clock) {
        this.coherencia = COHERENCIA_INICIAL;
        this.rachaActual = 0;
        this.rachaMaxima = 0;
        this.actualizadoEn = clock.now();
        return PUNTOS_LIGA_INICIAL - this.puntosLiga;
    }

    public static boolean correspondeBonoDeRacha(int racha) {
        return racha > 0 && racha % RACHA_BONO_CADA_DIAS == 0;
    }

    private static UserId requireId(UserId id) {
        return Objects.requireNonNull(id, "participanteId es obligatorio");
    }

    @Override
    public String toString() {
        return "PuntajeParticipante[" + participanteId + ", liga=" + puntosLiga + ", coherencia=" + coherencia + "]";
    }
}
