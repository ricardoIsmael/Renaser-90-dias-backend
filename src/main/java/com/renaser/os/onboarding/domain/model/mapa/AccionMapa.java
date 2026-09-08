package com.renaser.os.onboarding.domain.model.mapa;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Una accion motora del Mapa (V06): "verbo + objeto", con la frecuencia y los dias en que corre.
 *
 * <p><b>{@link #habitoId} es la mitad util de este agregado.</b> Al activar el mapa, cada accion se
 * convierte en un habito personal; guardar aca el habito que genero es lo que hace que tocar
 * "Activar" dos veces no cree dos habitos (AC-07 del manual). Mientras vale {@code null}, la accion
 * existe en el mapa y todavia no es un habito.
 *
 * <p>Los limites que dependen de OTRAS acciones (maximo 6, maximo 2 por area) no viven aca sino en
 * {@link AccionesDelMapa}: una accion suelta no puede saber cuantas hermanas tiene.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AccionMapa {

    private static final int MIN_CARACTERES = 5;
    private static final int MAX_CARACTERES = 100;

    private final UUID id;
    private final UserId usuarioId;
    /** El id que genera el cliente. Es la clave estable entre el telefono y la base. */
    private final String accionId;
    private final AreaMapa area;
    private final String texto;
    private final int frecuenciaSemanal;
    private final Set<DayOfWeek> dias;
    private final MomentoAccion momento;
    private final EvidenciaAccion evidencia;
    private UUID habitoId;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static AccionMapa crear(UUID id, UserId usuarioId, String accionId, AreaMapa area, String texto,
                                    int frecuenciaSemanal, Set<DayOfWeek> dias, MomentoAccion momento,
                                    EvidenciaAccion evidencia, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(area, "area es obligatoria");
        requireAccionId(accionId);
        requireTexto(texto);
        requireFrecuencia(frecuenciaSemanal);
        Instant ahora = clock.now();
        return new AccionMapa(id, usuarioId, accionId.trim(), area, texto.trim(), frecuenciaSemanal,
                diasInmutables(dias), momento, evidencia, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static AccionMapa rehydrate(UUID id, UserId usuarioId, String accionId, AreaMapa area, String texto,
                                        int frecuenciaSemanal, Set<DayOfWeek> dias, MomentoAccion momento,
                                        EvidenciaAccion evidencia, UUID habitoId, Instant creadoEn,
                                        Instant actualizadoEn) {
        return new AccionMapa(id, usuarioId, accionId, area, texto, frecuenciaSemanal, diasInmutables(dias),
                momento, evidencia, habitoId, creadoEn, actualizadoEn);
    }

    /**
     * Registra el habito que esta accion genero al activar el mapa. Idempotente a proposito: si ya
     * tenia uno, se conserva el original — reintentar "Activar" no puede reapuntar la accion a un
     * habito nuevo, que es exactamente como se duplicarian los habitos (AC-07).
     */
    public void vincularHabito(UUID nuevoHabitoId, Clock clock) {
        Objects.requireNonNull(nuevoHabitoId, "habitoId es obligatorio");
        if (this.habitoId != null) {
            return;
        }
        this.habitoId = nuevoHabitoId;
        this.actualizadoEn = clock.now();
    }

    public boolean yaEsHabito() {
        return habitoId != null;
    }

    private static void requireAccionId(String accionId) {
        if (accionId == null || accionId.isBlank()) {
            throw new IllegalArgumentException("accionId es obligatorio");
        }
    }

    private static void requireTexto(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() < MIN_CARACTERES || limpio.length() > MAX_CARACTERES) {
            throw new IllegalArgumentException(
                    "El texto de la accion debe tener entre " + MIN_CARACTERES + " y " + MAX_CARACTERES
                            + " caracteres, recibido: " + limpio.length());
        }
    }

    private static void requireFrecuencia(int frecuenciaSemanal) {
        if (frecuenciaSemanal < 1 || frecuenciaSemanal > 7) {
            throw new IllegalArgumentException(
                    "frecuenciaSemanal debe estar entre 1 y 7, recibido: " + frecuenciaSemanal);
        }
    }

    private static Set<DayOfWeek> diasInmutables(Set<DayOfWeek> dias) {
        return dias == null || dias.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(dias));
    }
}
