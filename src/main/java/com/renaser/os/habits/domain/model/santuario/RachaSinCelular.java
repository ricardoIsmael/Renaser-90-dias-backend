package com.renaser.os.habits.domain.model.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Racha "Dia sin celular" (tabla `rachas_sin_celular`) — honor-based, cruza
 * medianoche, hitos cada 3h hasta el ciclo completo de 24h. Traduccion 1:1 de
 * `phoneFree.ts`+`phoneFreeLadder.ts` (repo viejo, paso 0 en docs/MODULO_HABITS.md).
 *
 * <p>Distinta del Santuario ({@link SesionBloqueo}): esta no bloquea nada (la
 * app mide y confia) y romperla NO penaliza puntos — solo el cierre semanal sin
 * ningun ciclo completo cuesta puntos (ver `RevisionSemanalSinCelular`).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RachaSinCelular {

    /** Cada cuantas horas cae un hito (phoneFreeLadder.ts:21). */
    public static final int PASO_HITO_HORAS = 3;
    /** El ciclo completo, el unico que puntua (phoneFreeLadder.ts:24). */
    public static final int CICLO_COMPLETO_HORAS = 24;
    public static final int CICLO_COMPLETO_MINUTOS = CICLO_COMPLETO_HORAS * 60;
    /** Los 8 hitos validos como meta (phoneFreeLadder.ts:27). */
    public static final java.util.List<Integer> HITOS = java.util.List.of(3, 6, 9, 12, 15, 18, 21, 24);
    /** Duracion minima para que valga la pena registrar el cierre (phoneFree.ts:57). */
    public static final int MINUTOS_MINIMOS_CIERRE = 180;

    private final RachaSinCelularId id;
    private final UserId participanteId;
    private final RegistroHabitoId registroHabitoId;
    private final Instant iniciadaEn;
    private Instant terminadaEn;
    private final int horasObjetivo;
    private EstadoRacha estado;
    private Integer duracionMinutos;
    private String motivoRuptura;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static boolean esHorasObjetivoValida(int horas) {
        return HITOS.contains(horas);
    }

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code RachaService.iniciar}).
     */
    public static RachaSinCelular iniciar(RachaSinCelularId id, UserId participanteId,
                                           RegistroHabitoId registroHabitoId, int horasObjetivo, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(registroHabitoId, "registroHabitoId es obligatorio");
        if (!esHorasObjetivoValida(horasObjetivo)) {
            throw new IllegalArgumentException("La meta debe ser un multiplo de 3 hasta 24: " + horasObjetivo);
        }
        return new RachaSinCelular(id, participanteId, registroHabitoId, ahora, null,
                horasObjetivo, EstadoRacha.ACTIVA, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static RachaSinCelular rehydrate(RachaSinCelularId id, UserId participanteId,
                                             RegistroHabitoId registroHabitoId, Instant iniciadaEn,
                                             Instant terminadaEn, int horasObjetivo, EstadoRacha estado,
                                             Integer duracionMinutos, String motivoRuptura, Instant creadoEn,
                                             Instant actualizadoEn) {
        return new RachaSinCelular(id, participanteId, registroHabitoId, iniciadaEn, terminadaEn, horasObjetivo,
                estado, duracionMinutos, motivoRuptura, creadoEn, actualizadoEn);
    }

    /** Minutos vividos hasta {@code en}, con tope en el ciclo completo (phoneFreeLadder.ts:126-129). */
    public int minutosTranscurridos(Instant en) {
        long raw = Duration.between(iniciadaEn, en).toMinutes();
        return (int) Math.min(Math.max(raw, 0), CICLO_COMPLETO_MINUTOS);
    }

    /** El hito alcanzado con esa duracion: 0, 3, 6 ... 24 (phoneFreeLadder.ts:51-55). */
    public static int hitoAlcanzado(int minutosTranscurridos) {
        int pasoMinutos = PASO_HITO_HORAS * 60;
        if (minutosTranscurridos < pasoMinutos) {
            return 0;
        }
        int alcanzado = (minutosTranscurridos / pasoMinutos) * PASO_HITO_HORAS;
        return Math.min(alcanzado, CICLO_COMPLETO_HORAS);
    }

    public static boolean esCicloCompleto(int minutosTranscurridos) {
        return hitoAlcanzado(minutosTranscurridos) == CICLO_COMPLETO_HORAS;
    }

    /** Hasta cuando se acepta cerrar con evidencia: inicio + 24h + margen (phoneFreeLadder.ts:143-152). */
    public Instant plazoCierre(int horasExtensionConfiguradas) {
        return iniciadaEn.plus(Duration.ofMinutes(CICLO_COMPLETO_MINUTOS))
                .plus(Duration.ofMinutes(Math.max(horasExtensionConfiguradas, 0) * 60L));
    }

    /**
     * Cierra la racha con evidencia. {@code minutosMinimosCumplidos} y
     * {@code dentroDePlazo} son precondiciones ya verificadas por el llamador
     * (que conoce el reloj y el margen del habito) — el dominio solo aplica la
     * transicion y calcula si corresponde el ciclo completo.
     */
    public boolean cerrar(Instant ahora) {
        requireActiva();
        int minutos = minutosTranscurridos(ahora);
        boolean completo = esCicloCompleto(minutos);
        this.estado = completo ? EstadoRacha.COMPLETADA : EstadoRacha.ROTA;
        this.terminadaEn = ahora;
        this.duracionMinutos = minutos;
        this.actualizadoEn = ahora;
        return completo;
    }

    public void romper(String motivo, Instant ahora) {
        requireActiva();
        this.estado = EstadoRacha.ROTA;
        this.terminadaEn = ahora;
        this.duracionMinutos = minutosTranscurridos(ahora);
        this.motivoRuptura = motivo;
        this.actualizadoEn = ahora;
    }

    /** La racha vencio sin que nadie la cerrara (sweepExpiredRuns) — se limita al ciclo completo (phoneFree.ts:458-461). */
    public void expirar(Instant ahora) {
        requireActiva();
        this.estado = EstadoRacha.EXPIRADA;
        this.terminadaEn = ahora;
        this.duracionMinutos = Math.min(minutosTranscurridos(ahora), CICLO_COMPLETO_MINUTOS);
        this.actualizadoEn = ahora;
    }

    public boolean estaActiva() {
        return estado == EstadoRacha.ACTIVA;
    }

    private void requireActiva() {
        if (estado != EstadoRacha.ACTIVA) {
            throw new IllegalStateException("La racha ya no esta activa: " + estado);
        }
    }

    @Override
    public String toString() {
        return "RachaSinCelular[" + id + ", " + participanteId + ", " + estado + "]";
    }
}
