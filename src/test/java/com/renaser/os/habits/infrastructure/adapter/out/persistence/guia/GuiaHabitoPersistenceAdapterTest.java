package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.guia.ContenidoGuia;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GuiaHabitoPersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    @Autowired
    private GuiaHabitoPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;

    private HabitoId habitoId;

    @BeforeEach
    void seedHabito() {
        habitoId = HabitoId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", habitoId.value())
                .executeUpdate();
    }

    @Test
    void guardaYRecuperaUnaGuiaPorId() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA);
        guia.actualizarContenidoCompleto(
                new ContenidoGuia("hacer", "como", "ciencia", "renaser", "alquimia", "resultados", "titulo", "intro",
                        "cuerpo", "fuente"),
                AHORA);

        GuiaHabito guardada = adapter.save(guia);
        Optional<GuiaHabito> recuperada = adapter.byId(guardada.id());

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().queHacer()).isEqualTo("hacer");
        assertThat(recuperada.get().mantraTitulo()).isEqualTo("titulo");
        assertThat(recuperada.get().referenciaFuente()).isEqualTo("fuente");
    }

    @Test
    void porHabitoTraeTodasLasGuiasDelHabito() {
        adapter.save(GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA));
        adapter.save(GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 30, AHORA));

        List<GuiaHabito> guias = adapter.porHabito(habitoId);

        assertThat(guias).hasSize(2).extracting(GuiaHabito::diaInicio).containsExactlyInAnyOrder(1, 30);
    }

    @Test
    void masRecienteAbiertaIgnoraLasQueYaTienenDiaFin() {
        GuiaHabito cerrada = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA);
        cerrada.cerrarEn(10, AHORA);
        adapter.save(cerrada);
        GuiaHabito abierta = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 11, AHORA);
        adapter.save(abierta);

        Optional<GuiaHabito> resultado = adapter.masRecienteAbierta(habitoId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().diaInicio()).isEqualTo(11);
    }

    @Test
    void masRecienteAbiertaVaciaSiNingunaEstaAbierta() {
        GuiaHabito cerrada = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA);
        cerrada.cerrarEn(10, AHORA);
        adapter.save(cerrada);

        assertThat(adapter.masRecienteAbierta(habitoId)).isEmpty();
    }

    @Test
    void eliminarBorraLaGuia() {
        GuiaHabito guia = adapter.save(GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA));

        adapter.eliminar(guia.id());

        assertThat(adapter.byId(guia.id())).isEmpty();
    }
}
