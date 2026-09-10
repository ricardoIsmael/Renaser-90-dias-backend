package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.mentorprofile.SetMentorOperationalStatusUseCase.SetMentorOperationalStatusCommand;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase.UpdateMentorProfileCommand;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El semaforo operativo del mentor, separado del nivel (SDD 002, decision DL-09 del 2026-09-09).
 *
 * <p>Estos tests fallan contra el codigo anterior a esa decision: antes no existia
 * {@code setOperationalStatus} y el unico camino, {@code update}, exigia ADMIN/ALCHEMIST para
 * tocar el estado operativo — un MENTOR_LEAD recibia
 * <i>"Solo ADMIN/ALCHEMIST cambian nivel o estado operativo de un mentor"</i>.
 *
 * <p>Reloj a las <b>03:00 UTC</b>, que en America/Lima es el dia calendario ANTERIOR: la regla 02
 * del repositorio exige que un fixture no esconda un desfase de zona. Aca no hay logica de dia,
 * pero se fija asi igual para que el dia que la haya el fixture no la tape.
 */
class MentorProfileServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T03:00:00Z");

    private final LoadMentorProfilePort loadMentorProfilePort = mock(LoadMentorProfilePort.class);
    private final SaveMentorProfilePort saveMentorProfilePort = mock(SaveMentorProfilePort.class);
    private final RequireActiveUserGuard requireActiveUserGuard = mock(RequireActiveUserGuard.class);
    private final Clock clock = FixedClock.at(AHORA);

    private final MentorProfileService service = new MentorProfileService(
            loadMentorProfilePort, saveMentorProfilePort, requireActiveUserGuard, clock);

    private final UserId mentorId = UserId.of(UUID.randomUUID());

    // ─── lo que DL-09 concede ────────────────────────────────────────────────────────

    @Test
    @DisplayName("un MENTOR_LEAD mueve el semaforo operativo de un mentor")
    void unLiderDeMentoresMueveElSemaforo() {
        actor(UserRole.MENTOR_LEAD);
        MentorProfile perfil = perfilExistente();

        service.setOperationalStatus(
                new SetMentorOperationalStatusCommand(mentorId, MentorOperationalStatus.YELLOW, actorId));

        assertThat(perfil.operationalStatus()).isEqualTo(MentorOperationalStatus.YELLOW);
        assertThat(perfil.updatedAt()).isEqualTo(AHORA);
        verify(saveMentorProfilePort).save(perfil);
    }

    @ParameterizedTest
    @EnumSource(MentorOperationalStatus.class)
    @DisplayName("los tres colores del semaforo son alcanzables por el lider")
    void losTresColoresSonAlcanzables(MentorOperationalStatus color) {
        actor(UserRole.MENTOR_LEAD);
        MentorProfile perfil = perfilExistente();

        service.setOperationalStatus(new SetMentorOperationalStatusCommand(mentorId, color, actorId));

        assertThat(perfil.operationalStatus()).isEqualTo(color);
    }

    @Test
    @DisplayName("ADMIN y ALCHEMIST tambien: DL-09 amplia el conjunto, no lo reemplaza")
    void adminYAlchemistSiguenPudiendo() {
        for (UserRole rol : new UserRole[] {UserRole.ADMIN, UserRole.ALCHEMIST}) {
            actor(rol);
            MentorProfile perfil = perfilExistente();

            service.setOperationalStatus(
                    new SetMentorOperationalStatusCommand(mentorId, MentorOperationalStatus.RED, actorId));

            assertThat(perfil.operationalStatus()).isEqualTo(MentorOperationalStatus.RED);
        }
    }

    // ─── autorizacion negativa (regla 03: obligatoria para todo endpoint nuevo) ───────

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR"})
    @DisplayName("un TRAINEE o un MENTOR no mueven el semaforo de nadie")
    void nadieMasMueveElSemaforo(UserRole rol) {
        actor(rol);

        assertThatThrownBy(() -> service.setOperationalStatus(
                new SetMentorOperationalStatusCommand(mentorId, MentorOperationalStatus.GREEN, actorId)))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Solo MENTOR_LEAD/ADMIN/ALCHEMIST cambian el estado operativo de un mentor");
        verify(saveMentorProfilePort, never()).save(any());
    }

    /**
     * La otra mitad de DL-09, y la que mas facil se rompe: el semaforo se abrio, la promocion no.
     */
    @Test
    @DisplayName("un MENTOR_LEAD NO promueve de nivel: el nivel sigue siendo de ADMIN/ALCHEMIST")
    void unLiderDeMentoresNoPromueveDeNivel() {
        actor(UserRole.MENTOR_LEAD);
        perfilExistente();

        assertThatThrownBy(() -> service.update(
                new UpdateMentorProfileCommand(mentorId, MentorLevel.N3, null, null, actorId)))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Solo ADMIN/ALCHEMIST cambian nivel o estado operativo de un mentor");
        verify(saveMentorProfilePort, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────────

    private UserId actorId;

    private void actor(UserRole rol) {
        actorId = UserId.of(UUID.randomUUID());
        User usuario = User.rehydrate(actorId, new Email(actorId.value() + "@renaser.dev"), rol,
                UserStatus.ACTIVE, "Actor " + rol, null, null, null, null);
        when(requireActiveUserGuard.of(actorId)).thenReturn(usuario);
    }

    private MentorProfile perfilExistente() {
        MentorProfile perfil = MentorProfile.create(mentorId, FixedClock.at(AHORA.minusSeconds(86_400)));
        when(loadMentorProfilePort.byUserId(mentorId)).thenReturn(Optional.of(perfil));
        return perfil;
    }
}
