package com.renaser.os.habits.domain.model.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Reemplazo del rotulo de un habito por el propio aprendiz (tabla `renombres_habito`) —
 * hoy solo JUGO VERDE / AGUA TIBIA CON LIMON (renameableKeys.ts). Cambia SOLO el rotulo: el
 * {@code habitoId} sigue siendo el mismo, asi que horario, evidencia, puntos y categoria no
 * se tocan.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class RenombreHabito {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private String tituloPersonal;
    private String motivo;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static RenombreHabito crear(UserId participanteId, HabitoId habitoId, String tituloPersonal,
                                        String motivo, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        return new RenombreHabito(participanteId, habitoId, requireTitulo(tituloPersonal), requireMotivo(motivo),
                ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static RenombreHabito rehydrate(UserId participanteId, HabitoId habitoId, String tituloPersonal,
                                            String motivo, Instant creadoEn, Instant actualizadoEn) {
        return new RenombreHabito(participanteId, habitoId, tituloPersonal, motivo, creadoEn, actualizadoEn);
    }

    public void actualizar(String tituloPersonal, String motivo, Instant ahora) {
        this.tituloPersonal = requireTitulo(tituloPersonal);
        this.motivo = requireMotivo(motivo);
        this.actualizadoEn = ahora;
    }

    private static String requireTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo personal es obligatorio");
        }
        String recortado = titulo.trim();
        if (recortado.length() > 60) { // RENAME_TITLE_MAX_LENGTH, renameableKeys.ts
            throw new IllegalArgumentException("El titulo personal no puede superar los 60 caracteres");
        }
        return recortado;
    }

    private static String requireMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        String recortado = motivo.trim();
        if (recortado.length() > 200) { // RENAME_REASON_MAX_LENGTH, renameableKeys.ts
            throw new IllegalArgumentException("El motivo no puede superar los 200 caracteres");
        }
        return recortado;
    }

    @Override
    public String toString() {
        return "RenombreHabito[" + participanteId + ", " + habitoId + "]";
    }
}
