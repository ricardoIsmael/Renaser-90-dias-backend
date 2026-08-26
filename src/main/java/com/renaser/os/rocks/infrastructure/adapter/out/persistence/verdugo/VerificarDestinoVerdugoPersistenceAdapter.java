package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import com.renaser.os.rocks.application.ports.out.verdugo.VerificarDestinoVerdugoPort;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ver el javadoc de {@link VerificarDestinoVerdugoPort}: consulta acotada a
 * `renaser.registros_habito` (tabla de `habits`) solo para responder la pregunta de
 * pertenencia, sin `@Entity` propio ni tipos del otro modulo — la misma frontera que
 * respeta el resto de `rocks` cuando necesita leer algo que no le pertenece.
 */
@Component
class VerificarDestinoVerdugoPersistenceAdapter implements VerificarDestinoVerdugoPort {

    private static final String EXISTE_REGISTRO_DEL_PARTICIPANTE = """
            SELECT COUNT(*) FROM renaser.registros_habito
            WHERE id = ?1 AND participante_id = ?2
            """;

    private final EntityManager entityManager;

    VerificarDestinoVerdugoPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean registroHabitoPerteneceA(UUID registroHabitoId, UserId participanteId) {
        Number total = (Number) entityManager.createNativeQuery(EXISTE_REGISTRO_DEL_PARTICIPANTE)
                .setParameter(1, registroHabitoId)
                .setParameter(2, participanteId.value())
                .getSingleResult();
        return total.longValue() > 0;
    }
}
