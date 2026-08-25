package com.renaser.os.calendar.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descubierto sondeando la app real contra Postgres (los mocks de {@code EventoServiceTest}
 * nunca lo hubieran detectado): la version anterior de la query hacia
 * {@code FROM participantes_programa JOIN usuarios}, asi que un actor SIN fila en
 * `participantes_programa` — legitimo para ADMIN/ALCHEMIST, el programa de 90 dias es
 * obligatorio solo para APRENDIZ (baseline, tabla `participantes_programa`) — desaparecia
 * de {@code deParticipante} entero. Consecuencia real: un ADMIN no podia ni listar ni
 * administrar el calendario, {@code AccesoEventoService.requireProgreso} tiraba 404
 * "Participante no encontrado" para cualquier operacion.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class ConsultarProgresoParticipanteCalendarPersistenceAdapterTest {

    @Autowired
    private ConsultarProgresoParticipanteCalendarPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearUsuario(String rolCrudo, String estadoCrudo) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        return id;
    }

    private void crearParticipante(UserId id, int diaPrograma, String timezone) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                        VALUES (:id, :dia, :tz)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("tz", timezone)
                .executeUpdate();
    }

    @Test
    void devuelveDiaProgramaYZonaDeUnAprendizConFilaDeParticipante() {
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(id, 20, "America/Bogota");

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().diaPrograma()).isEqualTo(20);
        assertThat(progreso.get().zona()).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(progreso.get().rol()).isEqualTo(RolUsuario.TRAINEE);
        assertThat(progreso.get().suspendido()).isFalse();
    }

    @Test
    void unAdminSinFilaDeParticipanteApareceConDefaultsEnVezDeDesaparecer() {
        UserId id = crearUsuario("ADMIN", "ACTIVO");
        // Deliberadamente SIN insertar en participantes_programa: caso real de un ADMIN
        // que nunca se inscribio al programa de 90 dias.

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().rol()).isEqualTo(RolUsuario.ADMIN);
        assertThat(progreso.get().suspendido()).isFalse();
        assertThat(progreso.get().diaPrograma()).isZero();
        assertThat(progreso.get().celulaId()).isNull();
        assertThat(progreso.get().zona()).isEqualTo(ZoneId.of("America/Lima"));
    }

    @Test
    void unAlchemistSuspendidoSinFilaDeParticipanteSigueMarcandoSuspendido() {
        UserId id = crearUsuario("ALQUIMISTA", "SUSPENDIDO");

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().suspendido()).isTrue();
    }

    @Test
    void devuelveVacioSoloCuandoElUsuarioNoExiste() {
        assertThat(adapter.deParticipante(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
