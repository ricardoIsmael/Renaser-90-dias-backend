package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TokenPushPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private TokenPushPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID usuarioId;
    private UUID otroUsuarioId;

    private static TokenPushId nuevoId() {
        return TokenPushId.of(UUID.randomUUID());
    }

    @BeforeEach
    void crearPrerrequisitos() {
        usuarioId = UUID.randomUUID();
        otroUsuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Aprendiz de Prueba', 'APRENDIZ')
                """, usuarioId, "aprendiz-" + usuarioId + "@renaser.com");
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Otro Aprendiz', 'APRENDIZ')
                """, otroUsuarioId, "otro-" + otroUsuarioId + "@renaser.com");
    }

    @Test
    void upsertPorTokenInsertaUnoNuevo() {
        TokenPush registrado = adapter.upsertPorToken(
                TokenPush.registrar(nuevoId(), UserId.of(usuarioId), "expo-tok-nuevo", PlataformaPush.IOS,
                        CLOCK));

        assertThat(registrado.id()).isNotNull();
        assertThat(adapter.tokensDe(UserId.of(usuarioId))).containsExactly("expo-tok-nuevo");
    }

    @Test
    void upsertPorTokenConTokenYaExistenteReasignaSinDuplicar() {
        TokenPush primero = adapter.upsertPorToken(
                TokenPush.registrar(nuevoId(), UserId.of(usuarioId), "expo-tok-compartido", PlataformaPush.IOS,
                        CLOCK));

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(60));
        TokenPush segundo = adapter.upsertPorToken(
                TokenPush.registrar(nuevoId(), UserId.of(otroUsuarioId), "expo-tok-compartido",
                        PlataformaPush.ANDROID, masTarde));

        assertThat(segundo.id()).isEqualTo(primero.id()); // misma fila, no duplico
        assertThat(adapter.tokensDe(UserId.of(usuarioId))).isEmpty(); // ya no es del primero
        assertThat(adapter.tokensDe(UserId.of(otroUsuarioId))).containsExactly("expo-tok-compartido");
    }

    @Test
    void tokensDeDevuelveTodosLosDispositivosDeUnUsuario() {
        adapter.upsertPorToken(
                TokenPush.registrar(nuevoId(), UserId.of(usuarioId), "tok-a", PlataformaPush.IOS, CLOCK));
        adapter.upsertPorToken(
                TokenPush.registrar(nuevoId(), UserId.of(usuarioId), "tok-b", PlataformaPush.ANDROID, CLOCK));

        assertThat(adapter.tokensDe(UserId.of(usuarioId))).containsExactlyInAnyOrder("tok-a", "tok-b");
    }
}
