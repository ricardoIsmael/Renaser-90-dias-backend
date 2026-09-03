package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del barrido nocturno (docs/informes/habits-barrido-nocturno.md).
 * NO se corrieron ({@code ./mvnw} queda prohibido en este encargo porque el backend esta
 * corriendo con devtools) — quedan sin verificar hasta que alguien con permiso de build
 * las ejecute.
 */
@ExtendWith(MockitoExtension.class)
class GenerarTracksDelDiaSchedulerTest {

    @Mock
    private GenerarTracksDelDiaUseCase generarTracksUseCase;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;

    private GenerarTracksDelDiaScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new GenerarTracksDelDiaScheduler(generarTracksUseCase, progresoPort);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("ejecutar(): llama a generarDiaCompletoEnSuZona exactamente una vez por participante activo")
    void llamaAlCasoDeUsoUnaVezPorParticipante() {
        UserId p1 = participante();
        UserId p2 = participante();
        UserId p3 = participante();
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(p1, p2, p3));
        when(generarTracksUseCase.generarDiaCompletoEnSuZona(any())).thenReturn(List.of());

        scheduler.ejecutar();

        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(p1);
        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(p2);
        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(p3);
        verify(generarTracksUseCase, times(3)).generarDiaCompletoEnSuZona(any());
    }

    @Test
    @DisplayName("ejecutar(): un participante que falla no impide que se procesen los demas")
    void unParticipanteQueFallaNoDetieneElBarrido() {
        UserId falla = participante();
        UserId ok1 = participante();
        UserId ok2 = participante();
        // Orden deliberado: la falla va en el medio, para probar que el barrido sigue
        // despues de una excepcion y no corta el resto de la lista.
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ok1, falla, ok2));
        when(generarTracksUseCase.generarDiaCompletoEnSuZona(eq(falla)))
                .thenThrow(new IllegalStateException("zona horaria invalida"));
        when(generarTracksUseCase.generarDiaCompletoEnSuZona(eq(ok1))).thenReturn(List.of());
        when(generarTracksUseCase.generarDiaCompletoEnSuZona(eq(ok2)))
                .thenReturn(List.of());

        scheduler.ejecutar();

        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(ok1);
        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(falla);
        verify(generarTracksUseCase, times(1)).generarDiaCompletoEnSuZona(ok2);
    }

    @Test
    @DisplayName("ejecutar(): con el padron vacio no llama al caso de uso ni revienta")
    void padronVacioNoLlamaAlCasoDeUso() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of());

        scheduler.ejecutar();

        verify(generarTracksUseCase, never()).generarDiaCompletoEnSuZona(any());
    }

    @Test
    @DisplayName("ejecutar(): recorre el padron completo aunque TODOS los participantes fallen")
    void todosFallanIgualSeIntentanTodos() {
        UserId p1 = participante();
        UserId p2 = participante();
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(p1, p2));
        when(generarTracksUseCase.generarDiaCompletoEnSuZona(any()))
                .thenThrow(new RuntimeException("boom"));

        scheduler.ejecutar();

        InOrder orden = inOrder(generarTracksUseCase);
        orden.verify(generarTracksUseCase).generarDiaCompletoEnSuZona(p1);
        orden.verify(generarTracksUseCase).generarDiaCompletoEnSuZona(p2);
    }
}
