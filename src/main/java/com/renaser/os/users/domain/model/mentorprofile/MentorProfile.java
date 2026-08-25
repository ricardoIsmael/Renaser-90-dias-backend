package com.renaser.os.users.domain.model.mentorprofile;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Perfil del rol MENTOR (tabla `perfiles_mentor` de docs/db/sql/BD_NUEVA_V1.sql).
 *
 * Es el UNICO rol con perfil en tabla propia (decisión 2026-08-24, D-25): tiene 3 campos
 * propios y ya le sacaron uno (total_trainees_managed, P-17) — es una estructura que
 * crece de verdad, a diferencia de Alchemist/Admin (un solo texto, fusionados en User).
 * No hereda de una base compartida: al ser el unico caso, esa base ya no se justifica.
 *
 * `total_trainees_managed` NO vive aca: el SQL lo elimino a proposito (derivable
 * con COUNT sobre participantes_programa.mentor_id) para no duplicar un contador que
 * se puede calcular. No se agrega en el dominio por la misma razon.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "userId")
public final class MentorProfile {

    private final UserId userId;
    private MentorLevel level;
    private MentorOperationalStatus operationalStatus;
    private String bio;
    private final Instant createdAt;
    private Instant updatedAt;

    /** Un mentor nuevo arranca en N0 / GREEN (defaults del SQL). */
    public static MentorProfile create(UserId userId, Clock clock) {
        Instant now = clock.now();
        return new MentorProfile(Objects.requireNonNull(userId, "userId es obligatorio"),
                MentorLevel.N0, MentorOperationalStatus.GREEN, null, now, now);
    }

    /** Solo para el adaptador de persistencia: reconstruye un perfil ya existente. */
    public static MentorProfile rehydrate(UserId userId, MentorLevel level,
                                           MentorOperationalStatus operationalStatus, String bio,
                                           Instant createdAt, Instant updatedAt) {
        return new MentorProfile(userId, level, operationalStatus, bio, createdAt, updatedAt);
    }

    public void promoteTo(MentorLevel newLevel, Clock clock) {
        this.level = Objects.requireNonNull(newLevel, "newLevel es obligatorio");
        this.updatedAt = clock.now();
    }

    public void changeOperationalStatus(MentorOperationalStatus newStatus, Clock clock) {
        this.operationalStatus = Objects.requireNonNull(newStatus, "newStatus es obligatorio");
        this.updatedAt = clock.now();
    }

    public void updateBio(String newBio, Clock clock) {
        this.bio = newBio;
        this.updatedAt = clock.now();
    }

    @Override
    public String toString() {
        return "MentorProfile[" + userId + ", " + level + ", " + operationalStatus + "]";
    }
}
