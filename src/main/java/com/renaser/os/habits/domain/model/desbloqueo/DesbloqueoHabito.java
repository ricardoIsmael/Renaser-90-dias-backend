package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * En que dia de programa se desbloquea un habito para este participante (tabla
 * `desbloqueos_habito`) — resultado del escalonamiento por lotes del repo viejo
 * (`habitStaggering.ts`/`staggerService.ts`, ~1470 lineas, D-H2: el ALGORITMO de relleno
 * no se porto en esta pasada). Este agregado es SOLO LECTURA por ahora: expone lo que ya
 * este guardado en la tabla, sin reacomodar lotes.
 *
 * <p>{@code elegidoEn == null} significa que lo puso el relleno automatico, no el aprendiz
 * (semantica actual de la columna, documentada en el baseline).
 *
 * <p><b>Desde V23 (D-87) ya NO es solo lectura:</b> lleva el interruptor ACTIVO/PAUSADO del
 * aprendiz ({@code pausadoEn}). Pausar no es una relacion nueva sino un atributo de la que ya
 * existe — esta tabla ya era "que habitos lleva este aprendiz en su plan".
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class DesbloqueoHabito {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private final int diaDesbloqueo;
    private final Instant elegidoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /** `pausado_en` (V23): NULL = ACTIVO. Con valor = pausado, y desde cuando. */
    private Instant pausadoEn;

    /** Firma historica (sin `pausadoEn`): un desbloqueo sin pausa registrada esta activo. */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn, null);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn,
                                              Instant pausadoEn) {
        return new DesbloqueoHabito(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn,
                pausadoEn);
    }

    public boolean estaPausado() {
        return pausadoEn != null;
    }

    /**
     * Apaga el habito para ESTE aprendiz (D-87) — no toca el catalogo compartido, que es la
     * confusion que este cambio viene a cerrar: {@code habitos.activo} es global y de admin.
     *
     * <p>{@code desactivable} lo decide el llamador leyendo {@code habitos.desactivable} (V18):
     * la invariante cruza dos tablas, asi que no puede vivir en un CHECK ni resolverla este
     * agregado solo. Los cuatro habitos obligatorios no se pausan — si se pudieran, "obligatorio"
     * no querria decir nada.
     *
     * <p>Idempotente: pausar algo ya pausado no mueve la fecha original. Interesa CUANDO dejo de
     * hacerlo, no cuando volvio a tocar el boton.
     */
    public void pausar(boolean desactivable, Instant ahora) {
        if (!desactivable) {
            throw new IllegalStateException("Este habito es obligatorio y no se puede pausar");
        }
        if (estaPausado()) {
            return;
        }
        this.pausadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** Contraparte de {@link #pausar}. Idempotente: reactivar algo activo no cambia nada. */
    public void reactivar(Instant ahora) {
        if (!estaPausado()) {
            return;
        }
        this.pausadoEn = null;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "DesbloqueoHabito[" + participanteId + ", " + habitoId + ", dia " + diaDesbloqueo
                + (estaPausado() ? ", PAUSADO]" : ", ACTIVO]");
    }
}
