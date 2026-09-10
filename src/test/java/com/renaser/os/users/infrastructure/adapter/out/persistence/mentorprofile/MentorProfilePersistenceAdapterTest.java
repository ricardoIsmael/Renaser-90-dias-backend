package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.mentorprofile.EspecialidadMentor;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MentorProfilePersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private SaveUserPort userAdapter;

    @Autowired
    private LoadMentorProfilePort loadMentorProfilePort;

    @Autowired
    private SaveMentorProfilePort saveMentorProfilePort;

    @Test
    void guardaYRecuperaUnPerfilDeMentorConSusEnumsTraducidos() {
        UserId mentorId = crearMentorEnBaseDeDatos();

        MentorProfile profile = MentorProfile.create(mentorId, CLOCK);
        profile.promoteTo(MentorLevel.N2, CLOCK);
        profile.changeOperationalStatus(MentorOperationalStatus.YELLOW, CLOCK);
        profile.updateBio("Mentor de la celula Fenix", CLOCK);
        profile.cambiarEspecialidad(EspecialidadMentor.NEGOCIO, CLOCK);

        saveMentorProfilePort.save(profile);

        MentorProfile loaded = loadMentorProfilePort.byUserId(mentorId).orElseThrow();
        assertThat(loaded.level()).isEqualTo(MentorLevel.N2);
        assertThat(loaded.operationalStatus()).isEqualTo(MentorOperationalStatus.YELLOW);
        assertThat(loaded.bio()).isEqualTo("Mentor de la celula Fenix");
        assertThat(loaded.especialidad()).isEqualTo(EspecialidadMentor.NEGOCIO);
    }

    /** V48 dejo la columna nulable a proposito: los perfiles que ya existian no tienen ninguna, y
     * leerlos no puede fallar. */
    @Test
    void unPerfilSinEspecialidadSeLeeSinReventar() {
        UserId mentorId = crearMentorEnBaseDeDatos();

        saveMentorProfilePort.save(MentorProfile.create(mentorId, CLOCK));

        assertThat(loadMentorProfilePort.byUserId(mentorId).orElseThrow().especialidad()).isNull();
    }

    private UserId crearMentorEnBaseDeDatos() {
        UserId adminId = UserId.of(UUID.randomUUID());
        User admin = User.rehydrate(adminId, new Email("admin@renaser.com"), UserRole.ADMIN, UserStatus.ACTIVE,
                "Admin", null, null, null, null);
        UserId mentorId = UserId.of(UUID.randomUUID());
        User mentor = User.invite(mentorId, new Email("mentor@renaser.com"), "Mentor Uno", UserRole.MENTOR, admin);
        userAdapter.save(mentor);
        return mentorId;
    }
}
