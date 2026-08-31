package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
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
 * Contra Postgres real (Testcontainers), no un mock: es lo unico que prueba de verdad
 * que el mapeo UUID<->UserId, String<->Email y los enums en espanol<->ingles funcionan
 * (CLAUDE.MD §0.2 — obligatorio para todo adaptador nuevo).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserPersistenceAdapterTest {

    @Autowired
    private UserPersistenceAdapter adapter;

    @Test
    void guardaYRecuperaUnUsuarioConTodosSusCampos() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = User.registerTrainee(id, new Email("aprendiz@renaser.com"), "Ana Aprendiz");
        user.updateBio("no deberia guardarse para TRAINEE pero el campo es libre");
        user.changeAvatar("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + id);

        adapter.save(user);

        User loaded = adapter.byId(id).orElseThrow();
        assertThat(loaded.email()).isEqualTo(new Email("aprendiz@renaser.com"));
        assertThat(loaded.role()).isEqualTo(UserRole.TRAINEE);
        assertThat(loaded.fullName()).isEqualTo("Ana Aprendiz");
        assertThat(loaded.avatarUrl())
                .isEqualTo("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + id);
        assertThat(loaded.hasAccess()).isTrue();
    }

    @Test
    void traduceLosCincoRolesEnAmbasDirecciones() {
        for (UserRole role : UserRole.values()) {
            UserId id = UserId.of(UUID.randomUUID());
            User actor = User.rehydrate(UserId.of(UUID.randomUUID()), new Email("admin@renaser.com"),
                    UserRole.ADMIN, com.renaser.os.users.api.UserStatus.ACTIVE, "Admin",
                    null, null, null, null);
            User user = User.invite(id, new Email(role.name().toLowerCase() + "@renaser.com"),
                    "Usuario " + role, role, actor);

            adapter.save(user);

            assertThat(adapter.byId(id).orElseThrow().role()).isEqualTo(role);
        }
    }

    @Test
    void encuentraPorEmail() {
        UserId id = UserId.of(UUID.randomUUID());
        adapter.save(User.registerTrainee(id, new Email("buscame@renaser.com"), "Buscame"));

        assertThat(adapter.byEmail(new Email("buscame@renaser.com"))).isPresent();
        assertThat(adapter.byEmail(new Email("noexiste@renaser.com"))).isEmpty();
    }

    // ─── gap #5: baja de cuenta ─────────────────────────────────────────────

    @Test
    void deleteByIdBorraLaFilaYLibraElEmail() {
        UserId id = UserId.of(UUID.randomUUID());
        adapter.save(User.registerTrainee(id, new Email("borrame@renaser.com"), "Borrame"));

        adapter.deleteById(id);

        assertThat(adapter.byId(id)).isEmpty();
        assertThat(adapter.byEmail(new Email("borrame@renaser.com"))).isEmpty();
    }

    @Test
    void deleteByIdEsIdempotenteConUnIdInexistente() {
        UserId inexistente = UserId.of(UUID.randomUUID());

        assertThatCode(() -> adapter.deleteById(inexistente)).doesNotThrowAnyException();
    }

    @Test
    void pendingDeletionUpToSoloTraeLasQueTienenBajaSolicitadaDentroDelCorte() {
        UserId conBajaVencida = UserId.of(UUID.randomUUID());
        UserId sinBaja = UserId.of(UUID.randomUUID());
        Instant corte = Instant.parse("2026-08-20T00:00:00Z");
        User usuarioConBaja = User.registerTrainee(conBajaVencida, new Email("vencida@renaser.com"), "Vencida");
        usuarioConBaja.solicitarBaja(com.renaser.os.shared.domain.FixedClock.at(Instant.parse("2026-08-01T00:00:00Z")));
        adapter.save(usuarioConBaja);
        adapter.save(User.registerTrainee(sinBaja, new Email("sinbaja@renaser.com"), "Sin Baja"));

        var candidatas = adapter.pendingDeletionUpTo(corte);

        assertThat(candidatas).contains(conBajaVencida).doesNotContain(sinBaja);
    }
}
