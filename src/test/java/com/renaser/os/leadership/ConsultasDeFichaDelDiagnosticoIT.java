package com.renaser.os.leadership;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.ports.out.LeerConsultasDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.ContenidoDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Con Postgres real (spec 026): la lectura del diagnóstico de una persona deja su constancia en {@code consultas_de_ficha} (V157) sin tocar lo que ya hacía la
 * tabla. La base ata el tipo con la semana y con los roles; la fila de la lectura llega por el evento que publica {@code onboarding}, con el rol de la cuenta de
 * quien lo abrió, y reentregarlo no la duplica; y el resumen del mentor de las 20:00 sigue leyendo solo la ficha de una semana.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConsultasDeFichaDelDiagnosticoIT {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final UUID GRUPO = UUID.randomUUID();
    private static final Instant CUANDO = Instant.parse("2026-11-01T15:30:00Z");

    @Autowired private GuardarConsultaDeFichaPort guardar;
    @Autowired private LeerConsultasDeFichaPort leer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher eventos;
    @Autowired private PlatformTransactionManager transacciones;

    private static final String INSERTAR = """
            INSERT INTO renaser.consultas_de_ficha (id, consultor_id, rol_consultor, persona_id, grupo_id, mentor_id, semana_desde, consultada_en, tipo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private int filasDe(UserId persona, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ? AND tipo = ?", Integer.class, persona.value(), tipo);
    }

    @Test
    @DisplayName("el mentor, la guía, Soporte TI y el liderazgo dejan la constancia de un diagnóstico, sin semana")
    void guardaLaLecturaDeCadaRol() {
        UserId persona = UserId.of(UUID.randomUUID());
        for (UserRole rol : List.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.IT_SUPPORT, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST)) {
            guardar.insertar(ConsultaDeFicha.registrarDiagnostico(UUID.randomUUID(),
                    new ContenidoDelDiagnostico(UserId.of(UUID.randomUUID()), rol, persona, null, null), CUANDO));
        }

        assertThat(filasDe(persona, "DIAGNOSTICO")).isEqualTo(6);
        assertThat(jdbc.queryForList("SELECT semana_desde FROM renaser.consultas_de_ficha WHERE persona_id = ?", persona.value()))
                .allSatisfy(fila -> assertThat(fila.get("semana_desde")).isNull());
    }

    @Test
    @DisplayName("guardar dos veces la misma lectura deja una sola fila (el outbox puede reentregar el evento)")
    void sinDuplicados() {
        UserId persona = UserId.of(UUID.randomUUID());
        ConsultaDeFicha lectura = ConsultaDeFicha.registrarDiagnostico(UUID.randomUUID(),
                new ContenidoDelDiagnostico(UserId.of(UUID.randomUUID()), UserRole.ADMIN, persona, null, null), CUANDO);

        guardar.insertar(lectura);
        guardar.insertar(lectura);

        assertThat(filasDe(persona, "DIAGNOSTICO")).isEqualTo(1);
    }

    @Test
    @DisplayName("la base ata el tipo con la semana y con los roles: ninguna fila queda a medias")
    void laBaseNoDejaFilasATrapo() {
        UUID consultor = UUID.randomUUID();
        UUID persona = UUID.randomUUID();
        // Una semana sin su lunes, un diagnóstico con uno, un mentor leyendo una semana y un aprendiz leyendo un diagnóstico.
        assertThatThrownBy(() -> jdbc.update(INSERTAR, UUID.randomUUID(), consultor, "ADMIN", persona, GRUPO, null, null, java.sql.Timestamp.from(CUANDO), "SEMANA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERTAR, UUID.randomUUID(), consultor, "ADMIN", persona, GRUPO, null, java.sql.Date.valueOf(LocalDate.of(2026, 10, 10)),
                java.sql.Timestamp.from(CUANDO), "DIAGNOSTICO")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERTAR, UUID.randomUUID(), consultor, "MENTOR", persona, GRUPO, null, java.sql.Date.valueOf(LocalDate.of(2026, 10, 10)),
                java.sql.Timestamp.from(CUANDO), "SEMANA")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERTAR, UUID.randomUUID(), consultor, "TRAINEE", persona, GRUPO, null, null, java.sql.Timestamp.from(CUANDO), "DIAGNOSTICO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERTAR, UUID.randomUUID(), consultor, "ADMIN", persona, GRUPO, null, null, java.sql.Timestamp.from(CUANDO), "OTRA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la tabla sigue siendo append-only: una constancia de diagnóstico no se edita ni se borra")
    void sigueSiendoAppendOnly() {
        UserId persona = UserId.of(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        guardar.insertar(ConsultaDeFicha.registrarDiagnostico(id, new ContenidoDelDiagnostico(UserId.of(UUID.randomUUID()), UserRole.ADMIN, persona, null, null), CUANDO));

        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.consultas_de_ficha SET tipo = 'SEMANA' WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.consultas_de_ficha WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el resumen del mentor lee la ficha de una semana del líder y no sus lecturas del diagnóstico")
    void elResumenNoCuentaElDiagnostico() {
        UserId lider = UserId.of(UUID.randomUUID());
        UserId mentor = UserId.of(UUID.randomUUID());
        UserId persona = UserId.of(UUID.randomUUID());
        guardar.insertar(new ConsultaDeFicha(UUID.randomUUID(), lider, UserRole.MENTOR_LEAD, persona, GRUPO, mentor, LocalDate.of(2026, 10, 10), CUANDO));
        guardar.insertar(ConsultaDeFicha.registrarDiagnostico(UUID.randomUUID(), new ContenidoDelDiagnostico(lider, UserRole.MENTOR_LEAD, persona, GRUPO, mentor),
                CUANDO.plusSeconds(60)));

        List<ConsultaDeFicha> delLider = leer.delLiderEntre(List.of(mentor), CUANDO.minusSeconds(3600), CUANDO.plusSeconds(3600));

        assertThat(delLider).singleElement().satisfies(c -> assertThat(c.semanaDesde()).isEqualTo(LocalDate.of(2026, 10, 10)));
    }

    @Test
    @DisplayName("el evento de onboarding deja la fila con el rol de la cuenta de quien lo abrió, y reentregarlo no la duplica")
    void elEventoDejaLaConstancia() throws InterruptedException {
        UUID consultor = UUID.randomUUID();
        UserId persona = UserId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, 'Luisa Rojas', CAST('GUIA_APRENDIZ' AS renaser.rol_usuario))",
                consultor, consultor + "@renaser.test");
        try {
            DiagnosticoConsultadoEvent evento = new DiagnosticoConsultadoEvent(UUID.randomUUID(), UserId.of(consultor), persona, GRUPO, UserId.of(consultor), CUANDO);

            publicar(evento);
            esperarQueElOutboxTermine();
            publicar(evento);
            esperarQueElOutboxTermine();

            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT rol_consultor, tipo, grupo_id, consultada_en FROM renaser.consultas_de_ficha WHERE persona_id = ?", persona.value());
            assertThat(filas).singleElement().satisfies(fila -> {
                assertThat(fila.get("rol_consultor")).isEqualTo("TRAINEE_GUIDE");
                assertThat(fila.get("tipo")).isEqualTo("DIAGNOSTICO");
                assertThat(fila.get("grupo_id")).isEqualTo(GRUPO);
                assertThat(((java.sql.Timestamp) fila.get("consultada_en")).toInstant()).isEqualTo(CUANDO);
            });
        } finally {
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", consultor);
        }
    }

    private void publicar(DiagnosticoConsultadoEvent evento) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> eventos.publishEvent(evento));
    }

    /** Poll corto (sin Awaitility): los oyentes corren async después del commit. */
    private void esperarQueElOutboxTermine() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (pendientesEnElOutbox() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(pendientesEnElOutbox()).as("publicaciones sin completar en el outbox").isZero();
    }

    private int pendientesEnElOutbox() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
    }
}
