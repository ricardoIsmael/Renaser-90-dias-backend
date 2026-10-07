package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
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
    /** Snapshot del DIA, no del catalogo: {@code Habito.esOpcionalEnDia} (Ciclos de Intoxicacion, D-169). */
    private final boolean esOpcional;
    private EstadoRegistro estado;
    private int puntosOtorgados;
    private String respuestaTexto;
    private Integer calificacionProductividad;
    private java.util.UUID entradaDiarioId;
    private Instant completadoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /**
     * El número del día de un hábito medible (D-226: los km de {@code DAILY_KM}); {@code null} en
     * todos los demás, y en este mientras no se complete. Se escribe solo al completar.
     */
    private MedicionDiaria medicion;

    /**
     * Generado por el scheduler nocturno (o al activar el programa) — siempre PENDIENTE, 0 puntos.
     *
     * <p>El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code RegistroService}). Asi
     * {@code generar} es referencialmente transparente y un test puede fijar el id que espera,
     * en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static RegistroHabito generar(RegistroHabitoId id, UserId participanteId, HabitoId habitoId,
                                          LocalDate fechaEjecucion, int diaPrograma, TipoDia tipoDia,
                                          boolean esOpcional, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fechaEjecucion, "fechaEjecucion es obligatoria");
        Objects.requireNonNull(tipoDia, "tipoDia es obligatorio");
        if (diaPrograma < 0 || diaPrograma > 90) {
            throw new IllegalArgumentException("diaPrograma fuera de rango 0..90: " + diaPrograma);
        }
        return new RegistroHabito(id, participanteId, habitoId, fechaEjecucion, diaPrograma,
                tipoDia, esOpcional, EstadoRegistro.PENDIENTE, 0, null, null, null, null, ahora, ahora, null);
    }

    /** Solo para el adaptador de persistencia (y las pruebas): un registro sin medición. */
    public static RegistroHabito rehydrate(RegistroHabitoId id, UserId participanteId, HabitoId habitoId,
                                            LocalDate fechaEjecucion, int diaPrograma, TipoDia tipoDia,
                                            boolean esOpcional, EstadoRegistro estado, int puntosOtorgados,
                                            String respuestaTexto, Integer calificacionProductividad,
                                            java.util.UUID entradaDiarioId, Instant completadoEn, Instant creadoEn,
                                            Instant actualizadoEn) {
        return rehydrate(id, participanteId, habitoId, fechaEjecucion, diaPrograma, tipoDia, esOpcional, estado,
                puntosOtorgados, respuestaTexto, calificacionProductividad, entradaDiarioId, completadoEn, creadoEn,
                actualizadoEn, null);
    }

    /** Solo para el adaptador de persistencia: con la medición del día (D-226), o {@code null}. */
    public static RegistroHabito rehydrate(RegistroHabitoId id, UserId participanteId, HabitoId habitoId,
                                            LocalDate fechaEjecucion, int diaPrograma, TipoDia tipoDia,
                                            boolean esOpcional, EstadoRegistro estado, int puntosOtorgados,
                                            String respuestaTexto, Integer calificacionProductividad,
                                            java.util.UUID entradaDiarioId, Instant completadoEn, Instant creadoEn,
                                            Instant actualizadoEn, MedicionDiaria medicion) {
        return new RegistroHabito(id, participanteId, habitoId, fechaEjecucion, diaPrograma, tipoDia, esOpcional,
                estado, puntosOtorgados, respuestaTexto, calificacionProductividad, entradaDiarioId, completadoEn,
                creadoEn, actualizadoEn, medicion);
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
     * {@link ResultadoOtorgamiento}. Sin ventana configurada, desde D-97 el
     * llamador pasa el puntaje completo: la hora de la accion es el ancla
     * (antes pasaba 0, fiel a applyHabitAward del repo viejo).
     */
    public void completar(int puntos, String respuestaTexto, Integer calificacionProductividad,
                           java.util.UUID entradaDiarioId, Instant ahora) {
        completar(puntos, respuestaTexto, calificacionProductividad, entradaDiarioId, null, ahora);
    }

    /**
     * Igual que el de arriba, y además guarda el número del día (D-226). La medición se escribe en el
     * mismo gesto que completa, nunca antes ni despues: la base lo exige (CHECK
     * {@code registros_habito_medicion_solo_completado}, V84). Si la medición TIENE SENTIDO para el
     * hábito (mayor que cero, tope) no lo decide el registro sino la política del hábito.
     */
    public void completar(int puntos, String respuestaTexto, Integer calificacionProductividad,
                           java.util.UUID entradaDiarioId, MedicionDiaria medicion, Instant ahora) {
        /* No es `requireNoTerminal()` porque el mensaje de este gesto es otro, pero desde D-259 (2026-10-06) cubre
           lo mismo: COMPLETADO, FALLIDO y EXPIRADO quedan fuera. Entre el 2026-09 y D-259 EXPIRADO se podia
           completar (registrar tarde); desde E-534 EXPIRADO es solo de un dia que ya termino, y un habito de un dia
           terminado no se registra. Lo tarde DENTRO del dia sigue entrando: el registro esta PENDIENTE. Que el dia
           del registro no haya terminado lo comprueba el caso de uso ({@link #suDiaYaTermino}), porque la zona del
           participante no vive en el agregado. */
        if (!estado.puedeCompletarse()) {
            throw new IllegalStateException("Este registro no puede completarse: " + estado);
        }
        this.estado = EstadoRegistro.COMPLETADO;
        this.puntosOtorgados = Math.max(puntos, 0);
        this.respuestaTexto = respuestaTexto;
        this.calificacionProductividad = calificacionProductividad;
        this.entradaDiarioId = entradaDiarioId;
        this.medicion = medicion;
        this.completadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /**
     * D-259 — {@code true} si el dia local de este registro ya termino para el participante (su zona, no la del
     * servidor ni UTC; regla 02 §1). Es la misma frontera que el barrido de expiracion ({@link CorteDeExpiracion}):
     * un habito se registra durante su dia, aunque se le haya pasado la hora, y no despues.
     */
    public boolean suDiaYaTermino(java.time.ZoneId zona, Instant ahora) {
        return CorteDeExpiracion.para(zona, ahora).yaTermino(fechaEjecucion);
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
