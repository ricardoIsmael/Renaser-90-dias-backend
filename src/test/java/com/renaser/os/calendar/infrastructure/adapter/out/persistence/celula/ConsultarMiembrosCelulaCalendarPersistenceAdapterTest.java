package com.renaser.os.calendar.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El adaptador compone dos contratos publicos ({@code users} + {@code community}) y la
 * regla que justifica esa composicion — que el MENTOR de la celula entre en la audiencia
 * aunque NO tenga fila en {@code participantes_programa} — solo se puede comprobar contra
 * datos reales. Con un mock del finder el bug clasico ("el mentor no recibe los avisos de
 * los eventos de su propia celula") queda invisible.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarMiembrosCelulaCalendarPersistenceAdapterTest {

    @Autowired
    private ConsultarMiembrosCelulaCalendarPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void incluyeAlMentorQueLideraLaCelulaAunqueNoSeaParticipanteDelPrograma() {
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentor);
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(aprendiz, celulaId);

        assertThat(adapter.miembrosActivos(celulaId)).containsExactlyInAnyOrder(aprendiz, mentor);
    }

    @Test
    void dejaAfueraALosAprendicesSuspendidos() {
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentor);
        UserId activo = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearParticipante(activo, celulaId);
        crearParticipante(suspendido, celulaId);

        assertThat(adapter.miembrosActivos(celulaId)).contains(activo).doesNotContain(suspendido);
    }

    /** LinkedHashSet del adaptador: un mentor que ademas se inscribio al programa aparece una sola vez. */
    @Test
    void noDuplicaAlMentorQueTambienParticipaDelPrograma() {
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentor);
        crearParticipante(mentor, celulaId);

        assertThat(adapter.miembrosActivos(celulaId)).containsExactly(mentor);
    }

    @Test
    void unaCelulaQueNoExisteNoTieneDestinatarios() {
        assertThat(adapter.miembrosActivos(UUID.randomUUID())).isEmpty();
    }

    // ─── E-363: las asignaciones vigentes, ademas del puntero ─────────────────

    /** Contra el adaptador anterior falla: solo miraba {@code participantes_programa.celula_id}. */
    @Test
    @DisplayName("E-363: un aprendiz sumado a un segundo grupo (D-139) recibe los avisos de ese grupo y pertenece a el")
    void incluyeAlAprendizAdicionalPorSuAsignacionVigente() {
        UUID principal = crearCelula(crearUsuario("MENTOR", "ACTIVO"));
        UserId mentorDelAdicional = crearUsuario("MENTOR", "ACTIVO");
        UUID adicional = crearCelula(mentorDelAdicional);
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(aprendiz, principal);
        asignar(adicional, aprendiz, "APRENDIZ");

        assertThat(adapter.miembrosActivos(adicional)).containsExactlyInAnyOrder(mentorDelAdicional, aprendiz);
        assertThat(adapter.perteneceHoy(adicional, aprendiz)).isTrue();
        assertThat(adapter.perteneceHoy(principal, aprendiz)).as("sin asignacion en el principal").isFalse();
    }

    @Test
    @DisplayName("E-363: el mentor que lidera dos grupos (D-141) pertenece a los dos, y a ninguno mas")
    void elMentorDeDosGruposPerteneceALosDos() {
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UUID primero = crearCelula(mentor);
        UUID segundo = crearCelula(mentor);
        UUID ajeno = crearCelula(crearUsuario("MENTOR", "ACTIVO"));
        asignar(primero, mentor, "MENTOR");
        asignar(segundo, mentor, "MENTOR");

        assertThat(adapter.perteneceHoy(primero, mentor)).isTrue();
        assertThat(adapter.perteneceHoy(segundo, mentor)).isTrue();
        assertThat(adapter.perteneceHoy(ajeno, mentor)).isFalse();
    }

    @Test
    @DisplayName("E-363: un integrante adicional suspendido no recibe avisos, y una asignacion cerrada no cuenta")
    void dejaAfueraAlSuspendidoYALaAsignacionCerrada() {
        UUID grupo = crearCelula(crearUsuario("MENTOR", "ACTIVO"));
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        UserId quienSalio = crearUsuario("APRENDIZ", "ACTIVO");
        asignar(grupo, suspendido, "APRENDIZ");
        asignarCerrada(grupo, quienSalio);

        assertThat(adapter.miembrosActivos(grupo)).doesNotContain(suspendido, quienSalio);
        assertThat(adapter.perteneceHoy(grupo, quienSalio)).isFalse();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    private UserId crearUsuario(String rolCrudo, String estadoCrudo) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario),
                                CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        return id;
    }

    private UUID crearCelula(UserId mentorUsuarioId) {
        UUID cohorteId = UUID.randomUUID();
        entityManager.createNativeQuery(
                        "INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (:id, 'Cohorte test', current_date)")
                .setParameter("id", cohorteId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (:mentorId) ON CONFLICT DO NOTHING")
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

    private void asignar(UUID celulaId, UserId usuario, String funcion) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                        VALUES (:celula, :usuario, CAST(:funcion AS renaser.funcion_acompanamiento),
                                now() - interval '1 day', 'ADMINISTRATIVO', :clave)
                        """)
                .setParameter("celula", celulaId)
                .setParameter("usuario", usuario.value())
                .setParameter("funcion", funcion)
                .setParameter("clave", "prueba-" + UUID.randomUUID())
                .executeUpdate();
    }

    private void asignarCerrada(UUID celulaId, UserId usuario) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, fin, motivo,
                                                                 clave_operacion)
                        VALUES (:celula, :usuario, 'APRENDIZ', now() - interval '10 days', now() - interval '2 days',
                                'ADMINISTRATIVO', :clave)
                        """)
                .setParameter("celula", celulaId)
                .setParameter("usuario", usuario.value())
                .setParameter("clave", "prueba-" + UUID.randomUUID())
                .executeUpdate();
    }

    private void crearParticipante(UserId id, UUID celulaId) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id)
                        VALUES (:id, 10, 'America/Lima', :celulaId)
                        """)
                .setParameter("id", id.value())
                .setParameter("celulaId", celulaId)
                .executeUpdate();
    }
}
