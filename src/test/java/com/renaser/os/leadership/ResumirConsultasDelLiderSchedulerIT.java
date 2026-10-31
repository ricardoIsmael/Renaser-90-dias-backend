package com.renaser.os.leadership;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ResumirConsultasDelLiderUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * De punta a punta, con Postgres real: lo que el líder abrió ese día (spec 005, FR-016) llega, a las 20:10 de Lima, a la
 * bandeja del mentor como «Tu líder revisó a tu grupo» (FR-017), pasando por el outbox de Spring Modulith, y repetir la
 * corrida no crea un segundo aviso.
 *
 * <p><b>Es la prueba de la clase de E-250</b>: el unitario verifica que el evento se publica en su propia transacción; lo
 * que solo se ve con Spring de verdad es que el {@code @ApplicationModuleListener}, que corre después del commit, se
 * ejecuta. Escrita y no corrida en la máquina de desarrollo restringida (sin Testcontainers): la corre el {@code verify}
 * completo.
 *
 * <p>Se doblan solo el reloj (domingo 18, 20:10 en Lima = lunes 19, 01:10 UTC: el día de Lima y el de UTC no coinciden)
 * y el grupo de {@code community}. Todo lo demás —el registro de consultas, users, notifications— es lo real.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ResumirConsultasDelLiderSchedulerIT.Dobles.class})
class ResumirConsultasDelLiderSchedulerIT {

    private static final Instant LAS_VEINTE_DIEZ_EN_LIMA = Instant.parse("2026-10-19T01:10:00Z");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);
    private static final UUID FENIX = UUID.randomUUID();
    private static final UUID LIDER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID MENTORA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    @Autowired
    private ResumirConsultasDelLiderUseCase resumir;
    @Autowired
    private GuardarConsultaDeFichaPort consultas;
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RelojDePrueba reloj;

    @BeforeEach
    void fijarElReloj() {
        reloj.fijar(LAS_VEINTE_DIEZ_EN_LIMA);
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void elAvisoLlegaUnaSolaVezYSoloConLoQueMiroElLider() throws InterruptedException {
        sembrar();
        registrar(LIDER, UserRole.MENTOR_LEAD, ANA, Instant.parse("2026-10-18T15:00:00Z"));
        // La de las 23:30 de Lima ya es lunes en UTC y es del domingo: entra al resumen del domingo.
        registrar(LIDER, UserRole.MENTOR_LEAD, LUIS, Instant.parse("2026-10-18T04:30:00Z").plusSeconds(24 * 3600 - 60));
        // Administracion abre la misma ficha y queda registrado, pero no cuenta para el mentor.
        registrar(ADMIN, UserRole.ADMIN, LUIS, Instant.parse("2026-10-18T16:00:00Z"));

        assertThat(resumir.resumir()).isEqualTo(1);
        esperarQueElOutboxTermine();

        assertThat(notificacionesDe(MENTORA)).singleElement().satisfies(fila -> assertThat(fila)
                .containsEntry("tipo", "REVISION_DEL_LIDER")
                .containsEntry("titulo", "Tu líder revisó a tu grupo")
                .containsEntry("cuerpo", "Hoy revisó a Ana y Luis")
                .containsEntry("ruta_app", "/mentor/groups/" + FENIX + "/semaforo")
                .containsEntry("origen_evento_id", ReglaDelResumenDeConsultas.clave(UserId.of(MENTORA), DOMINGO)));

        // La segunda corrida de la misma ventana (20:40): la clave es la misma y la base no duplica.
        resumir.resumir();
        esperarQueElOutboxTermine();
        assertThat(notificacionesDe(MENTORA)).hasSize(1);
    }

    private void registrar(UUID consultor, UserRole rol, UUID persona, Instant cuando) {
        consultas.insertar(new ConsultaDeFicha(UUID.randomUUID(), UserId.of(consultor), rol, UserId.of(persona), FENIX,
                UserId.of(MENTORA), LocalDate.of(2026, 10, 10), cuando));
    }

    private void sembrar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?, ?, ?, ?)", LIDER, ADMIN, MENTORA, ANA, LUIS);
        usuario(LIDER, "LIDER_MENTORES", "Líder Uno");
        usuario(ADMIN, "ADMIN", "Admin Uno");
        usuario(MENTORA, "MENTOR", "Luisa Rojas");
        usuario(ANA, "APRENDIZ", "Ana María Pérez");
        usuario(LUIS, "APRENDIZ", "Luis Soto");
    }

    private void usuario(UUID id, String rol, String nombre) {
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))", id, id + "@renaser.test", nombre, rol);
    }

    private List<Map<String, Object>> notificacionesDe(UUID usuarioId) {
        return jdbc.queryForList("SELECT tipo::text AS tipo, titulo, cuerpo, ruta_app, origen_evento_id "
                + "FROM renaser.notificaciones WHERE usuario_id = ?", usuarioId);
    }

    /** Poll corto (sin Awaitility): los listeners corren async después del commit. Sin completar = no terminó. */
    private void esperarQueElOutboxTermine() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (pendientesEnElOutbox() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(pendientesEnElOutbox()).as("publicaciones sin completar en el outbox").isZero();
    }

    /** Sin esquema a propósito: V2 crea la tabla en el esquema por defecto de la conexión, no en renaser. */
    private int pendientesEnElOutbox() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
    }

    @TestConfiguration
    static class Dobles {

        @Bean
        @Primary
        AcompanamientoFinder grupoFenix() {
            AcompanamientoFinder grupos = mock(AcompanamientoFinder.class);
            when(grupos.gruposConMentorVigente(any())).thenReturn(List.of(new GrupoAcompanado(FENIX, "Grupo Fénix",
                    UserId.of(MENTORA), UUID.randomUUID(), "America/Lima", 3)));
            return grupos;
        }
    }
}
