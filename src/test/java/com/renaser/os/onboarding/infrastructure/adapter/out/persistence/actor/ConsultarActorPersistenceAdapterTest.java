package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.actor;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT contra Postgres real: confirma que este modulo lee el estado de suspension a traves
 * del contrato publico de `users` (D-41), no de una query propia contra `usuarios`.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarActorPersistenceAdapterTest {

    @Autowired
    private ConsultarActorPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearUsuario(String estadoCrudo) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        return id;
    }

    @Test
    void usuarioActivoNoEstaSuspendido() {
        UserId id = crearUsuario("ACTIVO");

        var actor = adapter.deActor(id);

        assertThat(actor).isPresent();
        assertThat(actor.get().suspendido()).isFalse();
    }

    @Test
    void usuarioSuspendidoQuedaMarcado() {
        UserId id = crearUsuario("SUSPENDIDO");

        var actor = adapter.deActor(id);

        assertThat(actor).isPresent();
        assertThat(actor.get().suspendido()).isTrue();
    }

    @Test
    void usuarioInexistenteDevuelveVacio() {
        assertThat(adapter.deActor(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
