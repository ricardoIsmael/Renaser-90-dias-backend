package com.renaser.os.habits.infrastructure.adapter.in.event;

import com.renaser.os.community.api.PublicacionCreadaEvent;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * E-121. Falla contra el codigo anterior por no compilar siquiera: no existia ningun oyente de
 * {@link PublicacionCreadaEvent} — el evento se publicaba y nadie lo escuchaba.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionCreadaHabitoListenerTest {

    @Mock
    private CerrarPostDiarioComunidadUseCase cerrarPostDiarioUseCase;

    private static PublicacionCreadaEvent evento(UserId autor, Instant occurredAt) {
        return new PublicacionCreadaEvent(UUID.randomUUID(), autor, "REFLEXION", occurredAt);
    }

    @Test
    @DisplayName("delega con el instante de la PUBLICACION, no con el de ahora (reentrega del outbox)")
    void delegaConElInstanteDeLaPublicacion() {
        UserId autor = UserId.of(UUID.randomUUID());
        Instant publicadoEn = Instant.parse("2026-08-24T15:00:00Z");

        new PublicacionCreadaHabitoListener(cerrarPostDiarioUseCase).on(evento(autor, publicadoEn));

        verify(cerrarPostDiarioUseCase).alPublicarEnElMuro(autor, publicadoEn);
    }

    @Test
    @DisplayName("un fallo cerrando el habito no se propaga: la publicacion ya ocurrio y es lo importante")
    void unFalloCerrandoElHabitoNoSePropaga() {
        UserId autor = UserId.of(UUID.randomUUID());
        doThrow(new IllegalStateException("El registro ya esta en un estado terminal: COMPLETADO"))
                .when(cerrarPostDiarioUseCase).alPublicarEnElMuro(any(), any());

        assertThatCode(() -> new PublicacionCreadaHabitoListener(cerrarPostDiarioUseCase)
                .on(evento(autor, Instant.parse("2026-08-24T15:00:00Z"))))
                .doesNotThrowAnyException();
    }
}
