package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;

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
    /**
     * `pausado_hasta` (V31): ultimo dia INCLUSIVE de la pausa, en la zona del participante. NULL
     * junto a un {@code pausadoEn} con valor = pausa indefinida, que es como se comportaba V23.
     */
    private LocalDate pausadoHasta;

    /** Firma historica (sin `pausadoEn`): un desbloqueo sin pausa registrada esta activo. */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn, null);
    }

    /** Firma de V23 (pausa sin fecha de fin), preservada para no tocar a quien ya la usaba. */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn,
                                              Instant pausadoEn) {
        return rehydrate(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn, pausadoEn,
                null);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static DesbloqueoHabito rehydrate(UserId participanteId, HabitoId habitoId, int diaDesbloqueo,
                                              Instant elegidoEn, Instant creadoEn, Instant actualizadoEn,
                                              Instant pausadoEn, LocalDate pausadoHasta) {
        return new DesbloqueoHabito(participanteId, habitoId, diaDesbloqueo, elegidoEn, creadoEn, actualizadoEn,
                pausadoEn, pausadoHasta);
    }

    /**
     * Si hay una pausa REGISTRADA, sin mirar el calendario. Para saber si el habito va HOY hay que
     * usar {@link #estaPausadoEl(LocalDate)}: una pausa con fecha de fin ya cumplida sigue teniendo
     * {@code pausadoEn} con valor, pero el habito ya volvio.
     */
    public boolean estaPausado() {
        return pausadoEn != null;
    }

    /**
     * Si el habito esta pausado ESE dia. Es la pregunta que importa para generar el dia y para
     * pintar el interruptor.
     *
     * <p>La reanudacion se DERIVA de la fecha; no la ejecuta ningun cron ni depende de que el
     * aprendiz vuelva a entrar (misma regla que .claude/rules/02: derivar, no acumular). Una pausa
     * "hasta el domingo" termina el domingo aunque el backend haya estado caido toda la semana.
     *
     * @param hoyEnSuZona fecha del participante en SU zona horaria, nunca la del servidor (E-91).
     */
    public boolean estaPausadoEl(LocalDate hoyEnSuZona) {
        if (pausadoEn == null) {
            return false;
        }
        return pausadoHasta == null || !hoyEnSuZona.isAfter(pausadoHasta);
    }

    /** Ultimo dia de la pausa, o {@code null} si es indefinida o si no hay pausa. */
    public LocalDate pausadoHasta() {
        return pausadoHasta;
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
        pausar(desactivable, null, ahora);
    }

    /**
     * Pausa con fecha de fin opcional (V31). {@code hasta = null} mantiene la pausa indefinida de
     * V23.
     *
     * <p>A diferencia de {@link #pausar(boolean, Instant)}, volver a pausar algo YA pausado SI
     * actualiza la fecha de fin: es la forma natural de extender o acortar una pausa vigente
     * ("mejor hasta el martes"). Lo que sigue sin moverse es {@code pausadoEn} — interesa cuando
     * dejo de hacerlo, no cuando toco el boton por ultima vez.
     */
    public void pausar(boolean desactivable, LocalDate hasta, Instant ahora) {
        if (!desactivable) {
            throw new IllegalStateException("Este habito es obligatorio y no se puede pausar");
        }
        if (!estaPausado()) {
            this.pausadoEn = ahora;
        }
        this.pausadoHasta = hasta;
        this.actualizadoEn = ahora;
    }

    /** Contraparte de {@link #pausar}. Idempotente: reactivar algo activo no cambia nada. */
    public void reactivar(Instant ahora) {
        if (!estaPausado()) {
            return;
        }
        this.pausadoEn = null;
        this.pausadoHasta = null;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "DesbloqueoHabito[" + participanteId + ", " + habitoId + ", dia " + diaDesbloqueo
                + (estaPausado() ? ", PAUSADO]" : ", ACTIVO]");
    }
}
