package com.renaser.os.habits.infrastructure.adapter.in.event;

import com.renaser.os.habits.application.ports.in.audiencia.AnotarCambioDeRolUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RolDeUsuarioCambiadoHabitsListenerTest {

    @Test
    @DisplayName("anota el cambio con el instante del evento, no con el reloj de ahora (reentrega del outbox)")
    void anotaConElInstanteDelEvento() {
        AnotarCambioDeRolUseCase anotar = mock(AnotarCambioDeRolUseCase.class);
        UserId persona = UserId.of(UUID.randomUUID());
        Instant cuando = Instant.parse("2026-10-12T15:00:00Z");

        new RolDeUsuarioCambiadoHabitsListener(anotar)
                .on(new RolDeUsuarioCambiadoEvent(persona, UserRole.TRAINEE, UserRole.MENTOR, cuando));

        verify(anotar).anotar(persona, "TRAINEE", "MENTOR", cuando);
    }
}
