package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
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
 * Implementacion CANONICA del patron ya probado por
 * `calendar.ConsultarProgresoParticipanteCalendarPersistenceAdapterTest`: el caso que
 * ya rompio produccion una vez (INNER JOIN hacia un ADMIN sin fila de programa
 * desapareciendo del resultado) es el primero que se cubre aca, explicitamente pedido
 * por la tarea.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class ConsultarResumenParticipacionPersistenceAdapterTest {

    @Autowired
    private ConsultarResumenParticipacionPersistenceAdapter adapter;

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

    private void crearParticipante(UserId id, int diaPrograma, String timezone, UUID celulaId, UserId mentorId) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id, mentor_id)
                        VALUES (:id, :dia, :tz, :celulaId, :mentorId)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("tz", timezone)
                .setParameter("celulaId", celulaId)
                .setParameter("mentorId", mentorId == null ? null : mentorId.value())
                .executeUpdate();
    }

    private UUID crearCelula(UserId mentorUsuarioId) {
        UUID cohorteId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (:id, 'Cohorte test', current_date)
                        """)
                .setParameter("id", cohorteId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                        INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (:mentorId)
                        """)
                .setParameter("mentorId", mentorUsuarioId.value())
                .executeUpdate();

        UUID celulaId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id)
                        VALUES (:id, 'Celula test', :mentorId, :cohorteId)
                        """)
                .setParameter("id", celulaId)
                .setParameter("mentorId", mentorUsuarioId.value())
                .setParameter("cohorteId", cohorteId)
                .executeUpdate();
        return celulaId;
    }

    @Test
    void devuelveInscritoConTodosLosCamposDeUnAprendizConFilaDeParticipante() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(id, 20, "America/Bogota", celulaId, mentorId);

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.inscrito()).isTrue();
        assertThat(resumen.diaPrograma()).isEqualTo(20);
        assertThat(resumen.zona()).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
        assertThat(resumen.celulaId()).isEqualTo(celulaId);
        assertThat(resumen.mentorId()).isEqualTo(mentorId);
        assertThat(resumen.rol()).isEqualTo(UserRole.TRAINEE);
        assertThat(resumen.suspendido()).isFalse();
    }

    /**
     * El caso que ya rompio produccion (ver docs/BITACORA_ERRORES.md / javadoc de
     * `ConsultarProgresoParticipanteCalendarPersistenceAdapter`): un ADMIN/ALCHEMIST sin
     * fila en `participantes_programa` (el programa es opcional para su rol) NO debe
     * desaparecer del resultado con un INNER JOIN — debe aparecer con `inscrito=false`
     * y defaults seguros.
     */
    @Test
    void unAdminSinFilaDeParticipanteApareceConInscritoFalseYDefaultsSeguros() {
        UserId id = crearUsuario("ADMIN", "ACTIVO");
        // Deliberadamente SIN insertar en participantes_programa.

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.inscrito()).isFalse();
        assertThat(resumen.rol()).isEqualTo(UserRole.ADMIN);
        assertThat(resumen.suspendido()).isFalse();
        assertThat(resumen.diaPrograma()).isZero();
        assertThat(resumen.celulaId()).isNull();
        assertThat(resumen.mentorId()).isNull();
        assertThat(resumen.zona()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
    }

    @Test
    void unAlchemistSuspendidoSinFilaDeParticipanteSigueMarcandoSuspendido() {
        UserId id = crearUsuario("ALQUIMISTA", "SUSPENDIDO");

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.suspendido()).isTrue();
        assertThat(resumen.inscrito()).isFalse();
    }

    @Test
    void devuelveVacioSoloCuandoElUsuarioNoExisteEnUsuarios() {
        assertThat(adapter.resumenDe(UserId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void miembrosActivosDeCelulaExcluyeSuspendidos() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId activo = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearParticipante(activo, 5, "America/Lima", celulaId, mentorId);
        crearParticipante(suspendido, 5, "America/Lima", celulaId, mentorId);

        var miembros = adapter.miembrosActivosDeCelula(celulaId);

        assertThat(miembros).containsExactly(activo);
    }

    @Test
    void contarMiembrosDeCelulaCuentaCualquierEstado() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId activo = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearParticipante(activo, 5, "America/Lima", celulaId, mentorId);
        crearParticipante(suspendido, 5, "America/Lima", celulaId, mentorId);

        assertThat(adapter.contarMiembrosDeCelula(celulaId)).isEqualTo(2);
    }
}
