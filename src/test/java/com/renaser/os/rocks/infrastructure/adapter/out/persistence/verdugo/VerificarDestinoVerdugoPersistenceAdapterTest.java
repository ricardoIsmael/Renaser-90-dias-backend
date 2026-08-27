package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirma, contra la base real, la pregunta abierta del hueco #18
 * (docs/PLAN_INTEGRACION_FRONTEND.md #18, "¿DestinoVerdugo acepta habitos personales por
 * FK?"): {@code registros_habito.habito_id} referencia la tabla unificada {@code habitos}
 * (P-12, SISTEMA y PERSONAL en la misma tabla) y esta consulta NUNCA joinea con
 * {@code habitos.ambito} — filtra solo por dueño (`participante_id`). Por eso un evento
 * Verdugo contra el track de un habito PERSONAL ya funciona hoy, sin cambio de codigo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VerificarDestinoVerdugoPersistenceAdapterTest {

    @Autowired
    private VerificarDestinoVerdugoPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
    }

    @Test
    void reconoceComoPropioUnRegistroDeUnHabitoPersonalDelParticipante() {
        UUID habitoPersonalId = seedHabito("PERSONAL", participanteId.value());
        UUID registroId = seedRegistro(habitoPersonalId, participanteId.value());

        assertThat(adapter.registroHabitoPerteneceA(registroId, participanteId)).isTrue();
    }

    @Test
    void reconoceComoPropioUnRegistroDeUnHabitoDeSistema() {
        UUID habitoSistemaId = seedHabito("SISTEMA", null);
        UUID registroId = seedRegistro(habitoSistemaId, participanteId.value());

        assertThat(adapter.registroHabitoPerteneceA(registroId, participanteId)).isTrue();
    }

    @Test
    void rechazaUnRegistroDeUnHabitoPersonalDeOtroParticipante() {
        UserId otro = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Otro', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", otro.value())
                .setParameter("email", otro + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", otro.value())
                .executeUpdate();
        UUID habitoDelOtro = seedHabito("PERSONAL", otro.value());
        UUID registroDelOtro = seedRegistro(habitoDelOtro, otro.value());

        assertThat(adapter.registroHabitoPerteneceA(registroDelOtro, participanteId)).isFalse();
    }

    private UUID seedHabito(String ambito, UUID participanteDuenio) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, participante_id, titulo, tipo, categoria_clave)
                        VALUES (:id, CAST(:ambito AS renaser.ambito_habito), :participanteId, 'Fixture', 'CHECKBOX',
                                'MENTE')
                        """)
                .setParameter("id", id)
                .setParameter("ambito", ambito)
                .setParameter("participanteId", participanteDuenio)
                .executeUpdate();
        return id;
    }

    private UUID seedRegistro(UUID habitoId, UUID participanteId) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.registros_habito
                            (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia)
                        VALUES (:id, :participanteId, :habitoId, :fecha, 20, CAST('DISCIPLINA' AS renaser.tipo_dia))
                        """)
                .setParameter("id", id)
                .setParameter("participanteId", participanteId)
                .setParameter("habitoId", habitoId)
                .setParameter("fecha", LocalDate.of(2026, 8, 24))
                .executeUpdate();
        return id;
    }
}
