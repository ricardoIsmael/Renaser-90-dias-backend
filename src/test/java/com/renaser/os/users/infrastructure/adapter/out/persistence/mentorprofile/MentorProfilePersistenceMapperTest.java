package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.EspecialidadMentor;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La especialidad de V48 cruzando el mapper. Es un POJO puro: la traduccion enum a enum se
 * comprueba sin Postgres — el ida y vuelta contra la columna real vive en
 * {@link MentorProfilePersistenceAdapterTest}.
 */
class MentorProfilePersistenceMapperTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T03:00:00Z"));

    private final MentorProfilePersistenceMapper mapper = new MentorProfilePersistenceMapper();

    @ParameterizedTest
    @EnumSource(EspecialidadMentor.class)
    @DisplayName("Las tres especialidades van y vuelven con el mismo nombre")
    void lasTresEspecialidadesSobrevivenLaIdaYVuelta(EspecialidadMentor especialidad) {
        MentorProfile perfil = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);
        perfil.cambiarEspecialidad(especialidad, CLOCK);

        MentorProfileJpaEntity fila = mapper.toEntity(perfil);

        assertThat(fila.getEspecialidad().name()).isEqualTo(especialidad.name());
        assertThat(mapper.toDomain(fila).especialidad()).isEqualTo(especialidad);
    }

    /**
     * A diferencia de los otros tres enums de esta tabla, este es NULABLE, y el null tiene que
     * sobrevivir el viaje: un {@code valueOf} a ciegas lo convertiria en un NPE al leer cualquiera
     * de los perfiles que ya existian antes de V48.
     */
    @Test
    @DisplayName("Un perfil sin especialidad va y vuelve en null, sin reventar")
    void sinEspecialidadNoRevienta() {
        MentorProfile perfil = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        MentorProfileJpaEntity fila = mapper.toEntity(perfil);

        assertThat(fila.getEspecialidad()).isNull();
        assertThat(mapper.toDomain(fila).especialidad()).isNull();
    }
}
