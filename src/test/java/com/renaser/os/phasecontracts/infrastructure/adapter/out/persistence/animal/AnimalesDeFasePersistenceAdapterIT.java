package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.animal;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AnimalesDeFasePersistenceAdapterIT {

    @Autowired
    private AnimalesDeFasePersistenceAdapter adapter;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("sin filas: las cuatro fases en orden y sin personalizar")
    void sinFilas() {
        assertThat(adapter.todos()).extracting(a -> a.fase().numero()).containsExactly(1, 2, 3, 4);
        assertThat(adapter.todos()).noneMatch(AnimalDeFase::tieneImagenPropia);
    }

    @Test
    @DisplayName("guardar y leer: imagen, nombre y autor; restaurar deja la imagen en null y conserva el nombre")
    void guardaYLee() {
        UserId autor = UserId.of(UUID.randomUUID());
        em.createNativeQuery("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (:id, :email, 'Admin Fixture', CAST('ADMIN' AS renaser.rol_usuario), 'ACTIVO')
                """).setParameter("id", autor.value()).setParameter("email", autor + "@renaser.test").executeUpdate();
        Instant ahora = Instant.parse("2026-10-06T03:00:00Z");
        AnimalDeFase a = adapter.porFase(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA);
        a.usarImagen("fases/animales/3/x", autor, ahora);
        a.nombrar("Caballo árabe", autor, ahora);
        adapter.guardar(a);
        em.clear();

        AnimalDeFase leido = adapter.porFase(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA);
        assertThat(leido.rutaImagen()).isEqualTo("fases/animales/3/x");
        assertThat(leido.nombre()).isEqualTo("Caballo árabe");
        assertThat(leido.actualizadoPor()).isEqualTo(autor);

        leido.restaurarImagen(autor, ahora);
        adapter.guardar(leido);
        em.clear();
        AnimalDeFase restaurado = adapter.porFase(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA);
        assertThat(restaurado.tieneImagenPropia()).isFalse();
        assertThat(restaurado.nombre()).isEqualTo("Caballo árabe");
    }

    @Test
    @DisplayName("la base rechaza una fase fuera de 1 a 4")
    void checkDeFase() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("INSERT INTO renaser.animales_de_fase (fase) VALUES (5)").executeUpdate();
            em.flush();
        }).isInstanceOf(Exception.class);
    }
}
