package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
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
class PreferenciaNotificacionPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private PreferenciaNotificacionPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

    @BeforeEach
    void crearPrerrequisitos() {
        usuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Aprendiz de Prueba', 'APRENDIZ')
                """, usuarioId, "aprendiz-" + usuarioId + "@renaser.com");
    }

    @Test
    void habilitadaParaSinFilaEsVacio() {
        var resultado = adapter.habilitadaPara(UserId.of(usuarioId), TipoNotificacion.MENSAJE_MENTOR);
        assertThat(resultado).isEmpty();
    }

    @Test
    void upsertInsertaYLuegoActualizaLaMismaFila() {
        UserId usuario = UserId.of(usuarioId);

        adapter.upsert(PreferenciaNotificacion.de(usuario, TipoNotificacion.MENSAJE_MENTOR, false, CLOCK));
        assertThat(adapter.habilitadaPara(usuario, TipoNotificacion.MENSAJE_MENTOR)).contains(false);

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(60));
        adapter.upsert(PreferenciaNotificacion.de(usuario, TipoNotificacion.MENSAJE_MENTOR, true, masTarde));
        assertThat(adapter.habilitadaPara(usuario, TipoNotificacion.MENSAJE_MENTOR)).contains(true);

        assertThat(adapter.porUsuario(usuario)).hasSize(1); // sigue siendo una sola fila, no duplico
    }

    @Test
    void traduceLos13TiposEnAmbasDirecciones() {
        UserId usuario = UserId.of(usuarioId);
        for (TipoNotificacion tipo : TipoNotificacion.values()) {
            adapter.upsert(PreferenciaNotificacion.de(usuario, tipo, false, CLOCK));
        }

        assertThat(adapter.porUsuario(usuario)).extracting(PreferenciaNotificacion::tipo)
                .containsExactlyInAnyOrder(TipoNotificacion.values());
    }
}
