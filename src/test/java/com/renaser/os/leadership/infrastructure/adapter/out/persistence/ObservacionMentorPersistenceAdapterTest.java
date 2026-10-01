package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code observaciones_mentor} (V89) contra Postgres real (D-241). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ObservacionMentorPersistenceAdapterTest {

    @Autowired
    private ObservacionMentorPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into renaser.usuarios (id, email, nombre_completo, rol) values (?, ?, ?, ?::renaser.rol_usuario)",
                id, id + "@renaser.test", "Persona de Prueba", rol);
        return UserId.of(id);
    }

    private ObservacionDeMentor nueva(UserId mentor, UserId lider, TipoObservacion tipo, String clave, Instant cuando) {
        return ObservacionDeMentor.registrar(UUID.randomUUID(),
                new Contenido(mentor, lider, tipo, "Texto de " + clave, Envio.SIN_ENVIAR, clave), FixedClock.at(cuando));
    }

    @Test
    @DisplayName("inserta, lee por clave y por mentor (lo mas nuevo primero) y cuenta por tipo en [desde, hasta)")
    void insertaYLee() {
        UserId mentor = usuario("MENTOR");
        UserId lider = usuario("LIDER_MENTORES");
        adapter.insertar(nueva(mentor, lider, TipoObservacion.ALERTA, "k1", Instant.parse("2026-09-10T15:00:00Z")));
        adapter.insertar(nueva(mentor, lider, TipoObservacion.RECONOCIMIENTO, "k2", Instant.parse("2026-09-20T15:00:00Z")));
        adapter.insertar(nueva(mentor, lider, TipoObservacion.ALERTA, "k3", Instant.parse("2026-10-01T05:00:00Z")));

        assertThat(adapter.porAutorYClave(lider, "k2")).get()
                .satisfies(o -> assertThat(o.tipo()).isEqualTo(TipoObservacion.RECONOCIMIENTO));
        assertThat(adapter.deMentor(mentor, null, 10)).extracting(ObservacionDeMentor::claveOperacion)
                .containsExactly("k3", "k2", "k1");
        assertThat(adapter.deMentor(mentor, Instant.parse("2026-09-20T15:00:00Z"), 10))
                .extracting(ObservacionDeMentor::claveOperacion).containsExactly("k1");
        var conteo = adapter.conteoPorMentor(Instant.parse("2026-09-01T05:00:00Z"), Instant.parse("2026-10-01T05:00:00Z"))
                .get(mentor);
        assertThat(conteo.alertas()).isEqualTo(1);
        assertThat(conteo.reconocimientos()).isEqualTo(1);
    }

    @Test
    @DisplayName("V89: la misma clave del mismo autor no entra dos veces")
    void claveUnica() {
        UserId mentor = usuario("MENTOR");
        UserId lider = usuario("LIDER_MENTORES");
        adapter.insertar(nueva(mentor, lider, TipoObservacion.ALERTA, "k1", Instant.parse("2026-09-10T15:00:00Z")));

        assertThatThrownBy(() -> adapter.insertar(
                nueva(mentor, lider, TipoObservacion.SUGERENCIA, "k1", Instant.parse("2026-09-11T15:00:00Z"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final String INSERT = "insert into renaser.observaciones_mentor (id, mentor_id, autor_id, tipo, "
            + "texto, enviada_por_chat, mensaje_id, clave_operacion, creado_en) values (?, ?, ?, ?, 'x', ?, ?, ?, now())";

    @Test
    @DisplayName("V89: la base rechaza un tipo fuera de los tres")
    void tipoFueraDeLosTres() {
        UserId mentor = usuario("MENTOR");
        UserId lider = usuario("LIDER_MENTORES");

        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), mentor.value(), lider.value(), "FELICITACION",
                false, null, "a")).hasMessageContaining("observaciones_mentor_tipo_check");
    }

    @Test
    @DisplayName("V89: la base rechaza un mensaje del chat sin envio")
    void mensajeSinEnvio() {
        UserId mentor = usuario("MENTOR");
        UserId lider = usuario("LIDER_MENTORES");

        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), mentor.value(), lider.value(), "ALERTA",
                false, UUID.randomUUID(), "b")).hasMessageContaining("observaciones_mentor_mensaje_coherente");
    }
}
