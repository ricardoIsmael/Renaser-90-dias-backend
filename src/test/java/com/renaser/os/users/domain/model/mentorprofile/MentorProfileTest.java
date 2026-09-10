package com.renaser.os.users.domain.model.mentorprofile;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorProfileTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void newMentorStartsAtN0Green() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(profile.level()).isEqualTo(MentorLevel.N0);
        assertThat(profile.operationalStatus()).isEqualTo(MentorOperationalStatus.GREEN);
        assertThat(profile.bio()).isNull();
    }

    @Test
    void promoteChangesLevelAndTimestamp() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        profile.promoteTo(MentorLevel.N2, later);

        assertThat(profile.level()).isEqualTo(MentorLevel.N2);
        assertThat(profile.updatedAt()).isEqualTo(later.now());
    }

    @Test
    void operationalStatusCanTurnYellowOrRed() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        profile.changeOperationalStatus(MentorOperationalStatus.RED, CLOCK);

        assertThat(profile.operationalStatus()).isEqualTo(MentorOperationalStatus.RED);
    }

    // ─── V48: especialidad del mentor ────────────────────────────────────────────────

    /** Sin declarar es un estado valido, no un perfil a medias: asi quedaron los que ya existian. */
    @Test
    @DisplayName("Un mentor nuevo nace SIN especialidad, no con una por defecto")
    void unMentorNuevoNaceSinEspecialidad() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(profile.especialidad()).isNull();
    }

    @Test
    @DisplayName("Declarar la especialidad la fija y mueve la marca de tiempo")
    void declararLaEspecialidadLaFija() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        profile.cambiarEspecialidad(EspecialidadMentor.MENTE, later);

        assertThat(profile.especialidad()).isEqualTo(EspecialidadMentor.MENTE);
        assertThat(profile.updatedAt()).isEqualTo(later.now());
    }

    /** "Sin especialidad" se hereda, no se pide: quien manda null confundio "no cambiar" con
     * "borrar", y eso lo filtra el caso de uso antes de llegar aca. */
    @Test
    @DisplayName("No se puede volver a dejar a un mentor sin especialidad pasando null")
    void noSeBorraLaEspecialidadConNull() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);
        profile.cambiarEspecialidad(EspecialidadMentor.NEGOCIO, CLOCK);

        assertThatThrownBy(() -> profile.cambiarEspecialidad(null, CLOCK))
                .isInstanceOf(NullPointerException.class);
        assertThat(profile.especialidad()).isEqualTo(EspecialidadMentor.NEGOCIO);
    }

    @Test
    @DisplayName("La sobrecarga vieja de rehydrate deja el perfil sin especialidad")
    void rehydrateViejoNoInventaEspecialidad() {
        MentorProfile profile = MentorProfile.rehydrate(UserId.of(UUID.randomUUID()), MentorLevel.N1,
                MentorOperationalStatus.GREEN, null, CLOCK.now(), CLOCK.now());

        assertThat(profile.especialidad()).isNull();
    }
}
