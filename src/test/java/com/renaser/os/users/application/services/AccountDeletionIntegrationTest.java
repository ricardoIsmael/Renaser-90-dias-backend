package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Contra Postgres real (Testcontainers), no mocks — es la prueba que exige el encargo:
 * "crear cuenta -> dar de baja -> purgar -> registrar de nuevo con el mismo email -> funciona".
 *
 * <p>Demuestra que el bug documentado del backend viejo NO se repite aca: alli, borrar una
 * cuenta no liberaba el email porque `wipeTraineeData` no tocaba ni `trainee_profiles` ni
 * `users` ni `account_requests`, y habia que borrar las 4 piezas a mano (ver
 * features/account-deletion/repository.ts#borrarCuenta). Aca, las ~30 FK contra `usuarios`
 * en el baseline son ON DELETE CASCADE (o SET NULL en las de auditoria), asi que un solo
 * DELETE de la fila raiz basta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountDeletionIntegrationTest {

    private static final int DIAS_DE_GRACIA = 14;

    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private DeleteUserPort deleteUserPort;

    @Test
    void crearBajaPurgarYRegistrarDeNuevoConElMismoEmailFunciona() {
        String email = "purgado-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock enElAlta = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId primeraCuentaId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(primeraCuentaId, new Email(email), "Primera Cuenta"));

        var requestService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                new RequireActiveUserGuard(loadUserPort), enElAlta, DIAS_DE_GRACIA);
        var estado = requestService.request(new RequestAccountDeletionCommand(primeraCuentaId, "ELIMINAR"));
        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();

        // Todavia dentro de la gracia: el cron no la toca.
        var purgaTemprana = requestService.purgeExpired();
        assertThat(purgaTemprana.purgadas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();

        // 15 dias despues (gracia de 14 ya vencida).
        FixedClock quinceDiasDespues = FixedClock.at(enElAlta.now().plusSeconds(15L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                new RequireActiveUserGuard(loadUserPort), quinceDiasDespues, DIAS_DE_GRACIA);

        var resultadoPurga = purgeService.purgeExpired();

        assertThat(resultadoPurga.purgadas()).isEqualTo(1);
        assertThat(resultadoPurga.fallidas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isEmpty();
        assertThat(loadUserPort.byEmail(new Email(email))).isEmpty();

        // El email tiene que estar libre para un alta nueva — este es el bug viejo que no debe repetirse.
        UserId segundaCuentaId = UserId.of(UUID.randomUUID());
        assertThatCode(() -> saveUserPort.save(
                User.registerTrainee(segundaCuentaId, new Email(email), "Segunda Cuenta")))
                .doesNotThrowAnyException();
        assertThat(loadUserPort.byId(segundaCuentaId)).isPresent();
        assertThat(loadUserPort.byEmail(new Email(email)).orElseThrow().id()).isEqualTo(segundaCuentaId);
    }

    @Test
    void cancelarLaBajaAntesDeQueVenzaLaGraciaEvitaLaPurga() {
        String email = "cancelada-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock clock = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId userId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(userId, new Email(email), "Se Arrepiente"));
        var service = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                new RequireActiveUserGuard(loadUserPort), clock, DIAS_DE_GRACIA);
        service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR"));

        service.cancel(userId);

        FixedClock muchoDespues = FixedClock.at(clock.now().plusSeconds(30L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                new RequireActiveUserGuard(loadUserPort), muchoDespues, DIAS_DE_GRACIA);

        var resultado = purgeService.purgeExpired();

        assertThat(resultado.purgadas()).isZero();
        assertThat(loadUserPort.byId(userId)).isPresent();
    }
}
