package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.user.Email;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class AccountRequestPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private AccountRequestPersistenceAdapter adapter;

    @Test
    void guardaYRecuperaUnaSolicitudPendiente() {
        UserId supabaseUserId = UserId.of(UUID.randomUUID());
        AccountRequest request = AccountRequest.submit(supabaseUserId, new Email("aspirante@renaser.com"),
                "Ana Aspirante", "+51999999999", "Lima", "203.0.113.5", CLOCK);

        var saved = adapter.save(request);

        AccountRequest loaded = adapter.byId(saved.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(AccountRequestStatus.PENDING);
        assertThat(loaded.email()).isEqualTo(new Email("aspirante@renaser.com"));
        assertThat(loaded.phone()).isEqualTo("+51999999999");
    }

    @Test
    void traduceLosTresEstadosEnAmbasDirecciones() {
        UserId adminId = UserId.of(UUID.randomUUID());
        com.renaser.os.users.domain.model.user.User admin =
                com.renaser.os.users.domain.model.user.User.rehydrate(adminId, new Email("admin@renaser.com"),
                        com.renaser.os.users.api.UserRole.ADMIN,
                        com.renaser.os.users.api.UserStatus.ACTIVE, "Admin", null, null, null, null);

        AccountRequest approved = AccountRequest.submit(UserId.of(UUID.randomUUID()),
                new Email("a@renaser.com"), "A", "+51900000001", null, null, CLOCK);
        approved.approve(admin, UserId.of(UUID.randomUUID()), CLOCK);
        adapter.save(approved);
        assertThat(adapter.byId(approved.id()).orElseThrow().status()).isEqualTo(AccountRequestStatus.APPROVED);

        AccountRequest rejected = AccountRequest.submit(UserId.of(UUID.randomUUID()),
                new Email("b@renaser.com"), "B", "+51900000002", null, null, CLOCK);
        rejected.reject(admin, "Datos incompletos", CLOCK);
        adapter.save(rejected);
        AccountRequest loadedRejected = adapter.byId(rejected.id()).orElseThrow();
        assertThat(loadedRejected.status()).isEqualTo(AccountRequestStatus.REJECTED);
        assertThat(loadedRejected.rejectionReason()).isEqualTo("Datos incompletos");
    }

    @Test
    void cuentaSolicitudesRecientesPorIpParaElRateLimit() {
        String ip = "198.51.100.7";
        adapter.save(AccountRequest.submit(UserId.of(UUID.randomUUID()), new Email("uno@renaser.com"), "Uno",
                "+51900000003", null, ip, CLOCK));
        adapter.save(AccountRequest.submit(UserId.of(UUID.randomUUID()), new Email("dos@renaser.com"), "Dos",
                "+51900000004", null, ip, CLOCK));

        long count = adapter.countSubmittedFromIpSince(ip, CLOCK.now().minusSeconds(3600));

        assertThat(count).isEqualTo(2);
    }
}
