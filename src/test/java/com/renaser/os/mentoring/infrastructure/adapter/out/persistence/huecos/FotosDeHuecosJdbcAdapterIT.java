package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.huecos;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.application.services.SemanasDeHuecosDePrueba;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code huecos_semana} (V120) contra Postgres real: que lo que se guarda se lee igual (con el horario que regía), que
 * la foto es historia —una segunda no la pisa— y que los CHECK aceptan lo que escribe el dominio y frenan lo que no.
 * Corre en una transacción que se deshace: no deja filas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FotosDeHuecosJdbcAdapterIT {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);
    private static final Instant CERRADA = Instant.parse("2026-10-26T05:45:00Z");

    @Autowired
    private FotosDeHuecosPort fotos;
    @Autowired
    private JdbcClient jdbcClient;

    private UserId persona;
    private UserId otra;

    @BeforeEach
    void personas() {
        persona = participante();
        otra = participante();
    }

    @Test
    @DisplayName("ida y vuelta: la semana guardada se lee igual, con la zona y la hora de cierre")
    void idaYVuelta() {
        SemanaDeHuecos semana = SemanasDeHuecosDePrueba.delContrato(LUNES);

        assertThat(fotos.registrar(new FotoDeHuecos(persona, ZoneId.of("America/Lima"), semana, CERRADA))).isTrue();

        FotoDeHuecos leida = fotos.deLaSemana(persona, LUNES).orElseThrow();
        assertThat(leida.semana()).isEqualTo(semana);
        assertThat(leida.zona()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(leida.cerradaEn()).isEqualTo(CERRADA);
        assertThat(jdbcClient.sql("SELECT minutos_ocupados FROM renaser.huecos_semana WHERE participante_id = :p")
                .param("p", persona.value()).query(Integer.class).single()).isEqualTo(semana.minutosOcupados());
    }

    @Test
    @DisplayName("append-only: una segunda foto de la misma semana no se guarda ni cambia la primera")
    void noSeReescribe() {
        SemanaDeHuecos semana = SemanasDeHuecosDePrueba.delContrato(LUNES);
        fotos.registrar(new FotoDeHuecos(persona, ZoneId.of("America/Lima"), semana, CERRADA));

        boolean segunda = fotos.registrar(new FotoDeHuecos(persona, ZoneId.of("Asia/Tokyo"), semana,
                CERRADA.plusSeconds(3600)));

        assertThat(segunda).isFalse();
        FotoDeHuecos leida = fotos.deLaSemana(persona, LUNES).orElseThrow();
        assertThat(leida.zona()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(leida.cerradaEn()).isEqualTo(CERRADA);
    }

    @Test
    @DisplayName("conFotoDe dice de quien hay foto de esa semana, en una consulta, y no mezcla personas ni semanas")
    void conFotoDe() {
        fotos.registrar(new FotoDeHuecos(persona, ZoneId.of("America/Lima"),
                SemanasDeHuecosDePrueba.delContrato(LUNES), CERRADA));
        fotos.registrar(new FotoDeHuecos(otra, ZoneId.of("America/Lima"),
                SemanasDeHuecosDePrueba.delContrato(LUNES.minusWeeks(1)), CERRADA));

        assertThat(fotos.conFotoDe(List.of(persona, otra), LUNES)).isEqualTo(Set.of(persona));
        assertThat(fotos.conFotoDe(List.of(), LUNES)).isEmpty();
        assertThat(fotos.deLaSemana(otra, LUNES)).isEmpty();
    }

    @Test
    @DisplayName("el CHECK de la base frena una semana que no empieza en lunes")
    void soloLunes() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                        INSERT INTO renaser.huecos_semana (participante_id, semana_inicio, zona, minutos_ocupados,
                               minutos_libres, cumplidos_a_tiempo, cumplidos_tarde, no_cumplidos, horario_que_regia,
                               cerrada_en)
                        VALUES (:p, :martes, 'America/Lima', 0, 0, 0, 0, 0, '[]'::jsonb, now())
                        """).param("p", persona.value()).param("martes", LUNES.plusDays(1)).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la foto cae con la cuenta (ON DELETE CASCADE contra participantes_programa)")
    void caeConElPrograma() {
        fotos.registrar(new FotoDeHuecos(persona, ZoneId.of("America/Lima"),
                SemanasDeHuecosDePrueba.delContrato(LUNES), CERRADA));

        jdbcClient.sql("DELETE FROM renaser.participantes_programa WHERE usuario_id = :p")
                .param("p", persona.value()).update();

        assertThat(fotos.deLaSemana(persona, LUNES)).isEmpty();
    }

    private UserId participante() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture huecos', 'APRENDIZ', 'ACTIVO')
                        """).param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 12, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", id).param("inicio", LUNES.minusDays(10))
                .param("activado", Timestamp.from(Instant.parse("2026-10-01T15:00:00Z"))).update();
        return UserId.of(id);
    }
}
