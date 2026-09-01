package com.renaser.os.community.domain.model.celula;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Grupo de aprendices con un mentor (tabla `celulas`). `mentorId` es el `usuario_id` de un
 * `perfiles_mentor` — tabla que NO pertenece a este modulo (es un perfil de `users`,
 * CLAUDE.MD sec. 5.3.2), asi que aca viaja como {@link UserId} plano: `community` guarda el
 * UUID, nunca importa el agregado de otro modulo (CLAUDE.MD sec. 5.1: "un modulo solo puede
 * llamar a la API publica de otro").
 *
 * <p>Un mentor lidera a lo sumo una celula (`celulas.mentor_id UNIQUE`,
 * V1__baseline_renaser.sql:245) — la unicidad la sostiene la base; el caso de uso rechaza
 * antes de llegar ahi (community/service.ts:389-393).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Celula {

    private final CelulaId id;
    private String nombre;
    private UserId mentorId;
    private final CohorteId cohorteId;
    private String urlVideollamada;
    private Instant proximaSesionEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code CelulaService.crear}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera, en
     * vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static Celula crear(CelulaId id, String nombre, CohorteId cohorteId, String urlVideollamada,
                                Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        requireNombreValido(nombre);
        Objects.requireNonNull(cohorteId, "cohorteId es obligatorio");
        return new Celula(id, nombre, null, cohorteId, urlVideollamada, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Celula rehydrate(CelulaId id, String nombre, UserId mentorId, CohorteId cohorteId,
                                    String urlVideollamada, Instant proximaSesionEn, Instant creadoEn,
                                    Instant actualizadoEn) {
        return new Celula(id, nombre, mentorId, cohorteId, urlVideollamada, proximaSesionEn, creadoEn, actualizadoEn);
    }

    public void actualizarDatos(String nombre, String urlVideollamada, boolean tocaUrl, Instant ahora) {
        String nombreEfectivo = nombre != null ? nombre : this.nombre;
        requireNombreValido(nombreEfectivo);
        this.nombre = nombreEfectivo;
        if (tocaUrl) {
            this.urlVideollamada = urlVideollamada;
        }
        this.actualizadoEn = ahora;
    }

    public void asignarMentor(UserId mentorId, Instant ahora) {
        this.mentorId = Objects.requireNonNull(mentorId, "mentorId es obligatorio");
        this.actualizadoEn = ahora;
    }

    public void quitarMentor(Instant ahora) {
        this.mentorId = null;
        this.actualizadoEn = ahora;
    }

    public void programarSesion(Instant proximaSesionEn, Instant ahora) {
        this.proximaSesionEn = Objects.requireNonNull(proximaSesionEn, "proximaSesionEn es obligatoria");
        this.actualizadoEn = ahora;
    }

    private static void requireNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la celula es obligatorio");
        }
        if (nombre.length() > 200) {
            throw new IllegalArgumentException("El nombre de la celula no puede pasar de 200 caracteres");
        }
    }

    @Override
    public String toString() {
        return "Celula[" + id + ", " + nombre + ", cohorte=" + cohorteId + "]";
    }
}
