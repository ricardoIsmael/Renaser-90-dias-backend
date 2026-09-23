package com.renaser.os.rag.infrastructure.adapter.out.academy;

import com.renaser.os.academy.api.ClaseDiariaPort;
import com.renaser.os.academy.api.CursosDelAprendizFinder;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.EstadoClase;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.Bloqueo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Traduccion de los contratos de {@code academy} a los puertos de {@code rag}, sin perder nada. */
class AdaptadoresDeAcademiaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    @Test
    void claseDeHoyLlevaElLargoDelResumenQueValidaAcademy() {
        ClaseDiariaPort api = mock(ClaseDiariaPort.class);
        when(api.claseDeHoy(APRENDIZ)).thenReturn(new ClaseDiariaPort.ClaseDeHoy(ClaseDiariaPort.Estado.DISPONIBLE,
                12, "c-1", "Fundamentos", "l-12", "Clase 12", false));
        when(api.entregar(APRENDIZ, "l-12", "texto de la persona")).thenReturn(new ClaseDiariaPort.Entrega("l-12", 8));
        ClaseDiariaDelAprendizAdapter adapter = new ClaseDiariaDelAprendizAdapter(api);

        assertThat(adapter.claseDeHoy(APRENDIZ)).isEqualTo(new ClaseDeHoy(EstadoClase.DISPONIBLE, 12, "Fundamentos",
                "l-12", "Clase 12", false, ClaseDiariaPort.RESUMEN_MIN_LENGTH, ClaseDiariaPort.RESUMEN_MAX_LENGTH));
        assertThat(adapter.entregar(APRENDIZ, "l-12", "texto de la persona")).isEqualTo(8);
    }

    @Test
    void bloqueoSeTraduceCampoACampo() {
        CursosDelAprendizFinder api = mock(CursosDelAprendizFinder.class);
        when(api.motivoBloqueoCurso(APRENDIZ, "c-2"))
                .thenReturn(new CursosDelAprendizFinder.MotivoBloqueo(true, "Liderazgo", 30, 12));

        assertThat(new ConsultarCursosAdapter(api).bloqueoDeCurso(APRENDIZ, "c-2"))
                .isEqualTo(new Bloqueo(true, "Liderazgo", 30, 12));
    }
}
