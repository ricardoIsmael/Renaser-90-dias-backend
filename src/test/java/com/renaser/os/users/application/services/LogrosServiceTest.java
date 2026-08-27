package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.HabitoLogrosFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.RadarLogrosFinder;
import com.renaser.os.users.api.RocaLogrosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.GetLogrosQuery;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.Logros;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogrosServiceTest {

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock
    private HabitoLogrosFinder habitoLogrosFinder;
    @Mock
    private RadarLogrosFinder radarLogrosFinder;
    @Mock
    private RocaLogrosFinder rocaLogrosFinder;

    private LogrosService service;

    @BeforeEach
    void setUp() {
        service = new LogrosService(new RequireActiveUserGuard(loadUserPort), participacionProgramaFinder,
                habitoLogrosFinder, radarLogrosFinder, rocaLogrosFinder);
    }

    private static User usuario(UserId id, UserStatus status) {
        return User.rehydrate(id, new Email(id + "@renaser.com"), UserRole.TRAINEE, status, "Fixture " + id, null,
                null, null, null);
    }

    private static ParticipacionPrograma inscripto(UserId id, int diaPrograma) {
        return new ParticipacionPrograma(id, true, diaPrograma, java.time.LocalDate.now(), ZoneId.of("America/Lima"),
                FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.TRAINEE, false);
    }

    @Test
    void componeLogrosAgregandoLosFindersDeHabitsRocksYUsers() {
        UserId traineeId = UserId.of(java.util.UUID.randomUUID());
        Instant primerHabito = Instant.parse("2026-01-10T00:00:00Z");
        Instant primerRoca = Instant.parse("2026-01-11T00:00:00Z");
        Instant primerRadar = Instant.parse("2026-01-12T00:00:00Z");

        when(loadUserPort.byId(traineeId)).thenReturn(Optional.of(usuario(traineeId, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.deParticipante(traineeId))
                .thenReturn(Optional.of(inscripto(traineeId, 42)));
        when(habitoLogrosFinder.totalHabitosCompletados(traineeId)).thenReturn(10L);
        when(habitoLogrosFinder.primerHabitoCompletadoEn(traineeId)).thenReturn(Optional.of(primerHabito));
        when(rocaLogrosFinder.totalRocksCompleted(traineeId)).thenReturn(20);
        when(rocaLogrosFinder.firstRockCompletedAt(traineeId)).thenReturn(Optional.of(primerRoca));
        when(rocaLogrosFinder.bestRocksStreakDays(traineeId)).thenReturn(5);
        when(radarLogrosFinder.totalRegistrosRadar(traineeId)).thenReturn(30L);
        when(radarLogrosFinder.primerRegistroRadarEn(traineeId)).thenReturn(Optional.of(primerRadar));

        Logros logros = service.getLogros(new GetLogrosQuery(traineeId));

        assertThat(logros.programDay()).isEqualTo(42);
        assertThat(logros.streak()).isNull();
        assertThat(logros.totalHabitsCompleted()).isEqualTo(10L);
        assertThat(logros.firstHabitCompletedAt()).isEqualTo(primerHabito);
        assertThat(logros.totalRocksCompleted()).isEqualTo(20);
        assertThat(logros.firstRockCompletedAt()).isEqualTo(primerRoca);
        assertThat(logros.bestRocksStreakDays()).isEqualTo(5);
        assertThat(logros.radarEntriesCount()).isEqualTo(30L);
        assertThat(logros.firstRadarEntryAt()).isEqualTo(primerRadar);
    }

    @Test
    void unFinderSinDatosTodaviaDevuelveNullEnVezDeFabricarUnaFecha() {
        UserId traineeId = UserId.of(java.util.UUID.randomUUID());

        when(loadUserPort.byId(traineeId)).thenReturn(Optional.of(usuario(traineeId, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.deParticipante(traineeId)).thenReturn(Optional.of(inscripto(traineeId, 1)));
        when(habitoLogrosFinder.totalHabitosCompletados(traineeId)).thenReturn(0L);
        when(habitoLogrosFinder.primerHabitoCompletadoEn(traineeId)).thenReturn(Optional.empty());
        when(rocaLogrosFinder.totalRocksCompleted(traineeId)).thenReturn(0);
        when(rocaLogrosFinder.firstRockCompletedAt(traineeId)).thenReturn(Optional.empty());
        when(rocaLogrosFinder.bestRocksStreakDays(traineeId)).thenReturn(0);
        when(radarLogrosFinder.totalRegistrosRadar(traineeId)).thenReturn(0L);
        when(radarLogrosFinder.primerRegistroRadarEn(traineeId)).thenReturn(Optional.empty());

        Logros logros = service.getLogros(new GetLogrosQuery(traineeId));

        assertThat(logros.firstHabitCompletedAt()).isNull();
        assertThat(logros.firstRockCompletedAt()).isNull();
        assertThat(logros.firstRadarEntryAt()).isNull();
        assertThat(logros.totalHabitsCompleted()).isZero();
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): SUSPENDED recibe 403 aunque su token
     * siga siendo valido. */
    @Test
    void unActorSuspendidoRecibeNotAuthorized() {
        UserId suspendidoId = UserId.of(java.util.UUID.randomUUID());
        when(loadUserPort.byId(suspendidoId)).thenReturn(Optional.of(usuario(suspendidoId, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.getLogros(new GetLogrosQuery(suspendidoId)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unUsuarioSinParticipacionEnElProgramaRecibeNotFound() {
        UserId staffId = UserId.of(java.util.UUID.randomUUID());
        when(loadUserPort.byId(staffId)).thenReturn(Optional.of(usuario(staffId, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.deParticipante(staffId))
                .thenReturn(Optional.of(new ParticipacionPrograma(staffId, false, 0, null,
                        ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.MENTOR,
                        false)));

        assertThatThrownBy(() -> service.getLogros(new GetLogrosQuery(staffId)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    /** {@code GetLogrosQuery} no tiene forma de llevar el id de OTRO usuario — es
     * self-only por construccion, mismo criterio que {@code RequestAccountDeletionCommand}. */
    @Test
    void elComandoEsSelfOnlyPorConstruccion() {
        assertThat(GetLogrosQuery.class.getRecordComponents()).hasSize(1);
        assertThat(GetLogrosQuery.class.getRecordComponents()[0].getName()).isEqualTo("actorId");
    }
}
