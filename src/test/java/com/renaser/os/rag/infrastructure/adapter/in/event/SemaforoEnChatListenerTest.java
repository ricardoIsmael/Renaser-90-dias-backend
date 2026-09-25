package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase;
import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase.SemaforoEnChatCommand;
import com.renaser.os.rag.domain.model.semaforo.ColorDeLaSemana;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Unit puro: el cierre llega al puerto con la persona, el color espejado, su palabra y la clave del evento. */
@ExtendWith(MockitoExtension.class)
class SemaforoEnChatListenerTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    @Mock
    private DejarSemaforoEnChatUseCase useCase;

    private final UUID persona = UUID.randomUUID();

    @Test
    void elCierreLlegaConColorPalabraPorcentajeYClave() {
        UUID clave = SemanaDelSemaforoCerradaEvent.claveDe(persona, VIERNES);

        new SemaforoEnChatListener(useCase).onSemanaCerrada(new SemanaDelSemaforoCerradaEvent(clave, persona,
                VIERNES.minusDays(6), VIERNES, new BigDecimal("72.4"), ColorSemaforo.AMARILLO, 6,
                Instant.parse("2026-09-26T05:25:03Z")));

        verify(useCase).dejarEnElChat(new SemaforoEnChatCommand(UserId.of(persona),
                new CierreDeSemana(ColorDeLaSemana.AMARILLO, "Requiere atención", new BigDecimal("72.4")), clave));
    }

    @Test
    void cadaColorTieneSuEspejoConElMismoNombre() {
        for (ColorSemaforo color : ColorSemaforo.values()) {
            assertThat(SemaforoEnChatListener.espejo(color).name()).isEqualTo(color.name());
        }
    }
}
