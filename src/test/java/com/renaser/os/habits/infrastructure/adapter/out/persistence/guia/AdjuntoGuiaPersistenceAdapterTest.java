package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.guia.SeccionGuia;
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
class AdjuntoGuiaPersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    @Autowired
    private AdjuntoGuiaPersistenceAdapter adapter;
    @Autowired
    private GuiaHabitoPersistenceAdapter guiaAdapter;
    @Autowired
    private EntityManager entityManager;

    private GuiaHabitoId guiaId;

    @BeforeEach
    void seedGuia() {
        HabitoId habitoId = HabitoId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", habitoId.value())
                .executeUpdate();
        guiaId = guiaAdapter.save(GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, AHORA)).id();
    }

    @Test
    void guardaYRecuperaUnAdjuntoDeEnlacePorId() {
        AdjuntoGuia adjunto = AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), guiaId, SeccionGuia.QUE_HACER,
                "https://youtube.com/x", "Titulo", 0, AHORA);

        AdjuntoGuia guardado = adapter.save(adjunto);
        Optional<AdjuntoGuia> recuperado = adapter.byId(guardado.id());

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().url()).isEqualTo("https://youtube.com/x");
        assertThat(recuperado.get().seccion()).isEqualTo(SeccionGuia.QUE_HACER);
        assertThat(recuperado.get().rutaStorage()).isNull();
    }

    @Test
    void porGuiasTraeTodosLosAdjuntosDeVariasGuiasEnUnaSolaConsulta() {
        adapter.save(AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), guiaId, SeccionGuia.QUE_HACER,
                "https://a", null, 0, AHORA));
        adapter.save(AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), guiaId, SeccionGuia.CIENCIA, "https://b",
                null, 1, AHORA));

        List<AdjuntoGuia> adjuntos = adapter.porGuias(List.of(guiaId));

        assertThat(adjuntos).hasSize(2);
    }

    @Test
    void eliminarBorraElAdjunto() {
        AdjuntoGuia guardado = adapter.save(
                AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), guiaId, SeccionGuia.QUE_HACER, "https://a",
                        null, 0, AHORA));

        adapter.eliminar(guardado.id());

        assertThat(adapter.byId(guardado.id())).isEmpty();
    }

    @Test
    void eliminarLaGuiaBorraEnCascadaSusAdjuntos() {
        adapter.save(AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), guiaId, SeccionGuia.QUE_HACER,
                "https://a", null, 0, AHORA));

        guiaAdapter.eliminar(guiaId);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.porGuias(List.of(guiaId))).isEmpty();
    }
}
