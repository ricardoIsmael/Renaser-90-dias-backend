package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-43 (docs/MODULOS_A_AVANZAR.md §8): prueba que la consulta en lote agrega
 * correctamente por participante+dia Y que hace UNA sola consulta sin
 * importar cuantos participantes se pidan — una implementacion con una
 * consulta por participante (el N+1 que la decision busca evitar) haria
 * fallar {@link #unaSolaConsultaParaVariosParticipantes()}.
 *
 * <p>{@code hibernate.generate_statistics} se activa solo para esta clase de
 * test (no en el {@code application.yaml} compartido) para no tocar
 * configuracion fuera de {@code habits/**}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ContarRegistrosDiariosHabitsPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final LocalDate HASTA = LocalDate.of(2026, 8, 24);
    private static final LocalDate DESDE = HASTA.minusDays(6);
    private static final LocalDate FUERA_DE_VENTANA = DESDE.minusDays(1);

    @Autowired
    private ContarRegistrosDiariosHabitsPersistenceAdapter adapter;

    @Autowired
    private RegistroHabitoPersistenceAdapter registroAdapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserId participante1;
    private UserId participante2;
    private HabitoId habitoObligatorio;
    private HabitoId habitoOpcional;

    @BeforeEach
    void seedFixtures() {
        participante1 = UserId.of(UUID.randomUUID());
        participante2 = UserId.of(UUID.randomUUID());
        habitoObligatorio = HabitoId.of(UUID.randomUUID());
        habitoOpcional = HabitoId.of(UUID.randomUUID());

        seedParticipante(participante1);
        seedParticipante(participante2);
        seedHabito(habitoObligatorio, "Meditar (lote)");
        seedHabito(habitoOpcional, "Extra opcional (lote)");

        // participante1, HASTA: 1 obligatorio COMPLETADO + 1 opcional SIN completar
        // -> calificables = 1 (el opcional no cuenta), completados = 1 -> 100%.
        RegistroHabito obligatorioP1 = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante1,
                habitoObligatorio, HASTA, 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        obligatorioP1.completar(10, null, null, null, CLOCK.now());
        registroAdapter.save(obligatorioP1);

        RegistroHabito opcionalP1 = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante1,
                habitoOpcional, HASTA, 5, TipoDia.DISCIPLINA, true, CLOCK.now());
        registroAdapter.save(opcionalP1);

        // participante1, fuera de la ventana pedida: no debe aparecer en el resultado.
        RegistroHabito fueraDeVentana = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante1,
                habitoObligatorio, FUERA_DE_VENTANA, 4, TipoDia.DISCIPLINA, false, CLOCK.now());
        registroAdapter.save(fueraDeVentana);

        // participante2, HASTA: 1 obligatorio COMPLETADO -> 100%, sin opcionales.
        RegistroHabito obligatorioP2 = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante2,
                habitoObligatorio, HASTA, 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        obligatorioP2.completar(10, null, null, null, CLOCK.now());
        registroAdapter.save(obligatorioP2);

        entityManager.flush();
    }

    @Test
    void agregaCorrectamentePorParticipanteYDia() {
        Map<UserId, List<ConteoDiarioHabitos>> resultado = adapter
                .contarPorParticipanteYDia(Set.of(participante1, participante2), DESDE, HASTA);

        assertThat(resultado.get(participante1)).containsExactly(
                new ConteoDiarioHabitos(HASTA, 2, 1, 1));
        assertThat(resultado.get(participante2)).containsExactly(
                new ConteoDiarioHabitos(HASTA, 1, 1, 0));
    }

    @Test
    void unaSolaConsultaParaVariosParticipantes() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        adapter.contarPorParticipanteYDia(Set.of(participante1, participante2), DESDE, HASTA);

        assertThat(statistics.getQueryExecutionCount())
                .as("una sola consulta para N participantes, no una por participante")
                .isEqualTo(1);
    }

    @Test
    void participantesSinRegistrosNoAparecenEnElResultado() {
        UserId sinRegistros = UserId.of(UUID.randomUUID());
        seedParticipante(sinRegistros);

        Map<UserId, List<ConteoDiarioHabitos>> resultado = adapter
                .contarPorParticipanteYDia(Set.of(sinRegistros), DESDE, HASTA);

        assertThat(resultado.getOrDefault(sinRegistros, List.of())).isEmpty();
    }

    private void seedParticipante(UserId id) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:usuarioId, 5)
                        """)
                .setParameter("usuarioId", id.value())
                .executeUpdate();
    }

    private void seedHabito(HabitoId id, String titulo) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", id.value())
                .setParameter("titulo", titulo)
                .executeUpdate();
    }
}
