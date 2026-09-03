package com.renaser.os.calendar.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort.ParticipanteConDia;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La traduccion {@link RolUsuario} -&gt; {@code users.api.UserRole} -&gt; enum
 * {@code renaser.rol_usuario} atraviesa tres vocabularios (D-21) y termina en un enum
 * nativo de Postgres. Es exactamente la clase de fallo que solo aparece ejecutando el SQL
 * (ver E-47 en docs/BITACORA_ERRORES.md: el enum comparado contra un {@code varchar}
 * reventaba en cada vuelta del scheduler, no al compilar).
 *
 * <p>La base NO esta vacia: todas las aserciones son {@code contains}/{@code doesNotContain}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ResolverAudienciaMasivaPersistenceAdapterTest {

    @Autowired
    private ResolverAudienciaMasivaPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void traineesActivosDejaAfueraAlSuspendidoYAlQueNoEsAprendiz() {
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");

        assertThat(adapter.traineesActivos()).contains(aprendiz).doesNotContain(suspendido, mentor);
    }

    @Test
    void activosConRolesTraduceCadaRolDelCalendarioAlEnumDePostgres() {
        UserId alquimista = crearUsuario("ALQUIMISTA", "ACTIVO");
        UserId admin = crearUsuario("ADMIN", "ACTIVO");
        UserId lider = crearUsuario("LIDER_MENTORES", "ACTIVO");
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");

        var todos = adapter.activosConRoles(Set.of(RolUsuario.ALCHEMIST, RolUsuario.ADMIN, RolUsuario.MENTOR_LEAD,
                RolUsuario.MENTOR, RolUsuario.TRAINEE));

        assertThat(todos).contains(alquimista, admin, lider, mentor, aprendiz);
    }

    @Test
    void activosConRolesFiltraPorElRolPedido() {
        UserId lider = crearUsuario("LIDER_MENTORES", "ACTIVO");
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");

        assertThat(adapter.activosConRoles(Set.of(RolUsuario.MENTOR_LEAD)))
                .contains(lider).doesNotContain(aprendiz);
    }

    @Test
    void activosConRolesNoConsultaCuandoNoHayRoles() {
        assertThat(adapter.activosConRoles(Set.of())).isEmpty();
    }

    @Test
    void traineesActivosConDiaProgramaTraeElDiaDeCadaAprendiz() {
        UserId inscrito = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(inscrito, 42);
        UserId sinInscribir = crearUsuario("APRENDIZ", "ACTIVO");

        var padron = adapter.traineesActivosConDiaPrograma();

        assertThat(padron).anySatisfy(fila -> {
            assertThat(fila.id()).isEqualTo(inscrito);
            assertThat(fila.diaPrograma()).isEqualTo(42);
        });
        assertThat(padron).extracting(ParticipanteConDia::id).contains(sinInscribir);
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

    private void crearParticipante(UserId id, int diaPrograma) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                        VALUES (:id, :dia, 'America/Lima')
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", (short) diaPrograma)
                .executeUpdate();
    }
}
