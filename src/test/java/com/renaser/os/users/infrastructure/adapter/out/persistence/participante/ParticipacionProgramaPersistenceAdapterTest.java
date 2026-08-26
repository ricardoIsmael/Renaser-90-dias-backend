package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ParticipacionProgramaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private SaveUserPort userAdapter;
    @Autowired
    private LoadParticipacionProgramaPort loadPort;
    @Autowired
    private SaveParticipacionProgramaPort savePort;
    @Autowired
    private DeleteParticipacionProgramaPort deletePort;

    private UserId crearUsuario(UserRole role) {
        UserId id = UserId.of(UUID.randomUUID());
        User user = User.rehydrate(id, new Email(id + "@renaser.com"), role,
                UserStatus.ACTIVE, "Fixture " + id, null, null, null, null);
        userAdapter.save(user);
        return id;
    }

    @Test
    void guardaYRecuperaUnaParticipacionConSusEnumsYZonaTraducidos() {
        UserId mentorUsuarioId = crearUsuario(UserRole.MENTOR);

        ParticipacionPrograma nueva = ParticipacionPrograma.activarSeguimientoPersonal(mentorUsuarioId, CLOCK);
        savePort.save(nueva);

        ParticipacionPrograma cargada = loadPort.byParticipanteId(mentorUsuarioId).orElseThrow();
        assertThat(cargada.diaPrograma()).isEqualTo(1);
        assertThat(cargada.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
        assertThat(cargada.timezone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(cargada.mentorId()).isNull();
        assertThat(cargada.celulaId()).isNull();
    }

    @Test
    void asignarMentorPersisteElCambio() {
        UserId traineeUsuarioId = crearUsuario(UserRole.TRAINEE);
        UserId mentorUsuarioId = crearUsuario(UserRole.MENTOR);
        savePort.save(ParticipacionPrograma.activarSeguimientoPersonal(traineeUsuarioId, CLOCK));

        ParticipacionPrograma participacion = loadPort.byParticipanteId(traineeUsuarioId).orElseThrow();
        participacion.asignarMentor(mentorUsuarioId, CLOCK);
        savePort.save(participacion);

        ParticipacionPrograma recargada = loadPort.byParticipanteId(traineeUsuarioId).orElseThrow();
        assertThat(recargada.mentorId()).isEqualTo(mentorUsuarioId);
    }

    @Test
    void borrarUnaParticipacionExistenteDevuelveTrueYLaElimina() {
        UserId usuarioId = crearUsuario(UserRole.ADMIN);
        savePort.save(ParticipacionPrograma.activarSeguimientoPersonal(usuarioId, CLOCK));

        boolean borrado = deletePort.deleteByParticipanteId(usuarioId);

        assertThat(borrado).isTrue();
        assertThat(loadPort.byParticipanteId(usuarioId)).isEmpty();
    }

    @Test
    void borrarUnaParticipacionInexistenteEsIdempotenteYDevuelveFalse() {
        UserId usuarioId = crearUsuario(UserRole.ADMIN);

        boolean borrado = deletePort.deleteByParticipanteId(usuarioId);

        assertThat(borrado).isFalse();
    }
}
