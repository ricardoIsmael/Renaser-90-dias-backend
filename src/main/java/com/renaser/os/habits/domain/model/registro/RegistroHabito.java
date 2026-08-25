package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * El track diario de un habito (tabla `registros_habito`) — el corazon del
 * modulo. Maquina de estados en {@link EstadoRegistro}, calculo de ventana en
 * {@link VentanaEntrega}, calculo de puntos en {@link ResultadoOtorgamiento}.
 *
 * <p>Traduccion 1:1 de las reglas de `service.ts` (paso 0, docs/MODULO_HABITS.md):
 * un registro nace PENDIENTE (generado por el scheduler nocturno), se completa
 * directo (CHECKBOX/JOURNALING/CALIFICACION) o pasa por EN_CURSO (BLOQUEO —
 * Santuario, y la racha sin celular que cuelga del mismo track). FALLIDO y
 * EXPIRADO son terminales: ninguna transicion los abandona.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RegistroHabito {

    private final RegistroHabitoId id;
    private final UserId participanteId;
    private final HabitoId habitoId;
    private final LocalDate fechaEjecucion;
    private final int diaPrograma;
    private final TipoDia tipoDia;
    private final boolean esOpcional;
    private EstadoRegistro estado;
    private int puntosOtorgados;
    private String respuestaTexto;
    private Integer calificacionProductividad;
    private java.util.UUID entradaDiarioId;
    private Instant completadoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /** Generado por el scheduler nocturno (o al activar el programa) — siempre PENDIENTE, 0 puntos. */
    public static RegistroHabito generar(UserId participanteId, HabitoId habitoId, LocalDate fechaEjecucion,
                                          int diaPrograma, TipoDia tipoDia, boolean esOpcional, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fechaEjecucion, "fechaEjecucion es obligatoria");
        Objects.requireNonNull(tipoDia, "tipoDia es obligatorio");
        if (diaPrograma < 0 || diaPrograma > 90) {
            throw new IllegalArgumentException("diaPrograma fuera de rango 0..90: " + diaPrograma);
        }
        return new RegistroHabito(RegistroHabitoId.newId(), participanteId, habitoId, fechaEjecucion, diaPrograma,
                tipoDia, esOpcional, EstadoRegistro.PENDIENTE, 0, null, null, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static RegistroHabito rehydrate(RegistroHabitoId id, UserId participanteId, HabitoId habitoId,
                                            LocalDate fechaEjecucion, int diaPrograma, TipoDia tipoDia,
                                            boolean esOpcional, EstadoRegistro estado, int puntosOtorgados,
                                            String respuestaTexto, Integer calificacionProductividad,
                                            java.util.UUID entradaDiarioId, Instant completadoEn, Instant creadoEn,
                                            Instant actualizadoEn) {
        return new RegistroHabito(id, participanteId, habitoId, fechaEjecucion, diaPrograma, tipoDia, esOpcional,
                estado, puntosOtorgados, respuestaTexto, calificacionProductividad, entradaDiarioId, completadoEn,
                creadoEn, actualizadoEn);
    }

    /** PENDIENTE -> EN_CURSO. Solo BLOQUEO (Santuario) y la racha sin celular lo usan. */
    public void iniciar(Instant ahora) {
        requireNoTerminal();
        if (!estado.puedeIniciar()) {
            throw new IllegalStateException("Solo un registro PENDIENTE puede iniciarse: " + estado);
        }
        this.estado = EstadoRegistro.EN_CURSO;
        this.actualizadoEn = ahora;
    }

    /**
     * PENDIENTE/EN_CURSO -> COMPLETADO. El llamador ya calculo el
     * {@link ResultadoOtorgamiento} (o pasa 0 puntos si el habito no tiene
     * ventana configurada — sin ventana, el repo viejo nunca otorga puntos,
     * ver applyHabitAward en service.ts).
     */
    public void completar(int puntos, String respuestaTexto, Integer calificacionProductividad,
                           java.util.UUID entradaDiarioId, Instant ahora) {
        requireNoTerminal();
        if (!estado.puedeCompletarse()) {
            throw new IllegalStateException("Este registro no puede completarse: " + estado);
        }
        this.estado = EstadoRegistro.COMPLETADO;
        this.puntosOtorgados = Math.max(puntos, 0);
        this.respuestaTexto = respuestaTexto;
        this.calificacionProductividad = calificacionProductividad;
        this.entradaDiarioId = entradaDiarioId;
        this.completadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** Vencio la ventana de entrega. Sin penalizacion — 0 puntos, ver docs/MODULO_HABITS.md paso 0. */
    public void expirar(Instant ahora) {
        if (!estado.puedeExpirar()) {
            return; // idempotente: ya es terminal
        }
        this.estado = EstadoRegistro.EXPIRADO;
        this.actualizadoEn = ahora;
    }

    /** Santuario roto (SALIDA_TEMPRANA/VIOLACION_APP_USADA) — unico camino a FALLIDO. */
    public void marcarFallido(Instant ahora) {
        if (!estado.puedeMarcarseFallido()) {
            throw new IllegalStateException("Este registro no puede marcarse FALLIDO: " + estado);
        }
        this.estado = EstadoRegistro.FALLIDO;
        this.actualizadoEn = ahora;
    }

    /**
     * EN_CURSO -&gt; PENDIENTE (si sigue siendo el dia de este registro) o EXPIRADO
     * (si ya no lo es) — libera un track cuya racha sin celular termino en un
     * hito parcial (releaseTrack en phoneFree.ts). No-op si no esta EN_CURSO:
     * no debe pisar un COMPLETADO llegado por otro camino.
     */
    public void liberar(boolean esDeHoy, Instant ahora) {
        if (estado != EstadoRegistro.EN_CURSO) {
            return;
        }
        this.estado = esDeHoy ? EstadoRegistro.PENDIENTE : EstadoRegistro.EXPIRADO;
        this.actualizadoEn = ahora;
    }

    private void requireNoTerminal() {
        if (estado.esTerminal()) {
            throw new IllegalStateException("El registro ya esta en un estado terminal: " + estado);
        }
    }

    @Override
    public String toString() {
        return "RegistroHabito[" + id + ", " + habitoId + ", " + fechaEjecucion + ", " + estado + "]";
    }
}
