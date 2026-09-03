package com.renaser.os.calendar.infrastructure.adapter.out.persistence.nivelmembresia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code niveles_membresia} no tiene seed (CL-2, docs/MODULO_CALENDAR.md §5): el catalogo
 * llega en una migracion de datos posterior. Este test verifica el contrato del adaptador
 * sembrando sus propias filas — el orden por rango, que es lo que
 * {@code ProgresoNivel.resolverRango} da por sentado, y el mapeo de columnas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NivelMembresiaPersistenceAdapterTest {

    @Autowired
    private NivelMembresiaPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void listarDevuelveLosNivelesOrdenadosPorRangoAscendente() {
        crearNivel(9, "Plata test", 40);
        crearNivel(7, "Bronce test", 10);

        List<NivelMembresia> niveles = adapter.listar();

        assertThat(niveles).isSortedAccordingTo(Comparator.comparingInt(NivelMembresia::rango));
        assertThat(niveles).extracting(NivelMembresia::rango).contains(7, 9);
    }

    @Test
    void listarMapeaNombreYPorcentajeMinimoDeCadaNivel() {
        crearNivel(8, "Oro test", 55);

        assertThat(adapter.listar()).anySatisfy(nivel -> {
            assertThat(nivel.rango()).isEqualTo(8);
            assertThat(nivel.nombre()).isEqualTo("Oro test");
            assertThat(nivel.pctProgresoMinimo()).isEqualTo(55);
            assertThat(nivel.id()).isPositive();
        });
    }

    private void crearNivel(int rango, String nombre, int pctMinimo) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.niveles_membresia (rango, nombre, pct_progreso_minimo)
                        VALUES (:rango, :nombre, :pct)
                        """)
                .setParameter("rango", (short) rango)
                .setParameter("nombre", nombre)
                .setParameter("pct", (short) pctMinimo)
                .executeUpdate();
    }
}
