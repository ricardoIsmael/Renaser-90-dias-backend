package com.renaser.os.mentoring.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El barrido de la foto semanal (spec 015) de punta a punta contra Postgres real y los módulos de verdad (horarios de
 * hábitos, acciones, eventos y agenda guardada): guarda la foto de la semana que acaba de terminar de una persona
 * activa, no la repite al correr otra vez, y no mide a una cuenta suspendida ni una semana anterior a su programa.
 * Corre en una transacción que se deshace.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CierreDeHuecosIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private CerrarHuecosDeLaSemanaUseCase cierre;
    @Autowired
    private FotosDeHuecosPort fotos;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcClient jdbcClient;

    /** El lunes de la última semana ya terminada en Lima: la que el barrido cierra. */
    private LocalDate semanaTerminada() {
        return clock.now().atZone(LIMA).toLocalDate().with(DayOfWeek.MONDAY).minusWeeks(1);
    }

    @Test
    @DisplayName("guarda la foto de la semana terminada de una persona activa, con sus siete dias, y una segunda corrida no la repite")
    void guardaYNoRepite() {
        UserId persona = aprendiz("ACTIVO", clock.now().atZone(LIMA).toLocalDate().minusDays(30));

        cierre.cerrarPendientes();

        Optional<FotoDeHuecos> foto = fotos.deLaSemana(persona, semanaTerminada());
        assertThat(foto).isPresent();
        assertThat(foto.get().zona()).isEqualTo(LIMA);
        assertThat(foto.get().semana().dias()).hasSize(7);
        assertThat(foto.get().semana().lunes()).isEqualTo(semanaTerminada());
        Instant primeraCierre = foto.get().cerradaEn();

        cierre.cerrarPendientes();

        assertThat(filas(persona)).isEqualTo(1);
        assertThat(fotos.deLaSemana(persona, semanaTerminada()).orElseThrow().cerradaEn()).isEqualTo(primeraCierre);
    }

    @Test
    @DisplayName("una cuenta suspendida no se mide")
    void suspendida() {
        UserId persona = aprendiz("SUSPENDIDO", clock.now().atZone(LIMA).toLocalDate().minusDays(30));

        cierre.cerrarPendientes();

        assertThat(filas(persona)).isZero();
    }

    @Test
    @DisplayName("una semana anterior al Dia 1 de la persona no se guarda")
    void antesDelPrograma() {
        UserId persona = aprendiz("ACTIVO", clock.now().atZone(LIMA).toLocalDate().plusDays(3));

        cierre.cerrarPendientes();

        assertThat(filas(persona)).isZero();
    }

    private int filas(UserId persona) {
        return jdbcClient.sql("SELECT count(*) FROM renaser.huecos_semana WHERE participante_id = :p")
                .param("p", persona.value()).query(Integer.class).single();
    }

    private UserId aprendiz(String estado, LocalDate fechaInicio) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture cierre huecos', 'APRENDIZ', CAST(:estado AS renaser.estado_usuario))
                        """).param("id", id).param("email", id + "@renaser.test").param("estado", estado).update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 12, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", id).param("inicio", fechaInicio)
                .param("activado", Timestamp.from(clock.now().minusSeconds(86_400L * 30))).update();
        return UserId.of(id);
    }
}
