package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** IT con Testcontainers — mismo patron de prerrequisitos manuales (usuarios) que
 * `points.AjustePuntosPersistenceAdapterTest`. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class NotificacionPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private NotificacionPersistenceAdapter adapter;
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
    void guardarAsignaIdAutogenerado() {
        Notificacion n = Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.SANTUARIO_ROTO, "Titulo",
                "Cuerpo", "/ruta", CLOCK);

        Notificacion guardada = adapter.guardar(n);

        assertThat(guardada.id()).isNotNull();
        assertThat(guardada.rutaApp()).isEqualTo("/ruta");
    }

    @Test
    void traduceLos13TiposEnAmbasDirecciones() {
        for (TipoNotificacion tipo : TipoNotificacion.values()) {
            Notificacion n = Notificacion.emitir(UserId.of(usuarioId), tipo, "T", "C", null, CLOCK);
            Notificacion guardada = adapter.guardar(n);
            assertThat(guardada.tipo()).isEqualTo(tipo);
        }
    }

    @Test
    void bandejaDevuelveMasNuevaPrimeroDentroDeLaVentana() {
        FixedClock hace100Dias = FixedClock.at(CLOCK.now().minusSeconds(100L * 24 * 3600));
        FixedClock hace1Dia = FixedClock.at(CLOCK.now().minusSeconds(24 * 3600));

        Notificacion vieja = adapter.guardar(
                Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "vieja", "c", null,
                        hace100Dias));
        Notificacion reciente = adapter.guardar(
                Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "reciente", "c", null,
                        hace1Dia));

        var desde = CLOCK.now().minusSeconds(90L * 24 * 3600);
        var bandeja = adapter.bandeja(UserId.of(usuarioId), desde, 100);

        assertThat(bandeja).extracting(Notificacion::id).contains(reciente.id()).doesNotContain(vieja.id());
    }

    @Test
    void bandejaRespetaElLimite() {
        for (int i = 0; i < 5; i++) {
            adapter.guardar(Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA,
                    "n" + i, "c", null, CLOCK));
        }

        var bandeja = adapter.bandeja(UserId.of(usuarioId), CLOCK.now().minusSeconds(3600), 3);

        assertThat(bandeja).hasSize(3);
    }

    @Test
    void marcarLeidaEsAtomicoYSoloAfectaLaPropia() {
        UUID otroUsuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Otro Aprendiz', 'APRENDIZ')
                """, otroUsuarioId, "otro-" + otroUsuarioId + "@renaser.com");

        Notificacion propia = adapter.guardar(
                Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "t", "c", null, CLOCK));

        int actualizadas = adapter.marcarLeida(propia.id(), UserId.of(otroUsuarioId), CLOCK.now());
        assertThat(actualizadas).isZero(); // no es de otroUsuarioId

        actualizadas = adapter.marcarLeida(propia.id(), UserId.of(usuarioId), CLOCK.now());
        assertThat(actualizadas).isEqualTo(1);

        // repetirlo (ya leida) devuelve 0 -> idempotencia se resuelve en el service via existeDe
        actualizadas = adapter.marcarLeida(propia.id(), UserId.of(usuarioId), CLOCK.now());
        assertThat(actualizadas).isZero();
    }

    @Test
    void existeDeDistingueDuenoDeNoDueno() {
        Notificacion propia = adapter.guardar(
                Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "t", "c", null, CLOCK));

        assertThat(adapter.existeDe(propia.id(), UserId.of(usuarioId))).isTrue();
        assertThat(adapter.existeDe(propia.id(), UserId.of(UUID.randomUUID()))).isFalse();
        assertThat(adapter.existeDe(-1L, UserId.of(usuarioId))).isFalse();
    }

    @Test
    void marcarTodasLeidasCuentaSoloLasNoLeidasDelUsuario() {
        adapter.guardar(Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "a", "c", null,
                CLOCK));
        adapter.guardar(Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA, "b", "c", null,
                CLOCK));

        int actualizadas = adapter.marcarTodasLeidas(UserId.of(usuarioId), CLOCK.now());
        assertThat(actualizadas).isEqualTo(2);

        int segundaVez = adapter.marcarTodasLeidas(UserId.of(usuarioId), CLOCK.now());
        assertThat(segundaVez).isZero(); // idempotente
    }

    @Test
    void purgarAnterioresABorraSoloLasViejas() {
        FixedClock hace100Dias = FixedClock.at(CLOCK.now().minusSeconds(100L * 24 * 3600));
        Notificacion vieja = adapter.guardar(Notificacion.emitir(UserId.of(usuarioId), TipoNotificacion.ANUNCIO_SISTEMA,
                "vieja", "c", null, hace100Dias));
        Notificacion reciente = adapter.guardar(Notificacion.emitir(UserId.of(usuarioId),
                TipoNotificacion.ANUNCIO_SISTEMA, "reciente", "c", null, CLOCK));

        int purgadas = adapter.purgarAnterioresA(CLOCK.now().minusSeconds(90L * 24 * 3600));

        assertThat(purgadas).isEqualTo(1);
        assertThat(adapter.existeDe(vieja.id(), UserId.of(usuarioId))).isFalse();
        assertThat(adapter.existeDe(reciente.id(), UserId.of(usuarioId))).isTrue();
    }
}
