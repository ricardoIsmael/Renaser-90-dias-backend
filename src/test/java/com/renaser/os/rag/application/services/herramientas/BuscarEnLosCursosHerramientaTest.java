package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.ConsultarLeccionesVisiblesPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code buscar_en_los_cursos} (D-255): SER busca en el material de los cursos con la consulta que
 * arma el modelo, con el mismo gate de lecciones visibles que el material del turno.
 */
class BuscarEnLosCursosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final VectorStorePort vectorStorePort = mock(VectorStorePort.class);
    private final ConsultarLeccionesVisiblesPort visiblesPort = mock(ConsultarLeccionesVisiblesPort.class);
    private final BuscarEnLosCursosHerramienta herramienta = new BuscarEnLosCursosHerramienta(vectorStorePort,
            visiblesPort);

    private ResultadoHerramienta buscar(String consulta) {
        return herramienta.ejecutar(APRENDIZ, new InvocacionHerramienta(BuscarEnLosCursosHerramienta.NOMBRE,
                Map.of(BuscarEnLosCursosHerramienta.ARGUMENTO_CONSULTA, consulta)));
    }

    @Test
    @DisplayName("busca la consulta del modelo solo entre las lecciones que la persona ve hoy, y devuelve el texto")
    void buscaConElGateDeLeccionesVisibles() {
        when(visiblesPort.visiblesParaActor(APRENDIZ)).thenReturn(Set.of("leccion-dia-3"));
        when(vectorStorePort.buscarSimilares("ritual de la manana pasos", 5,
                FiltroLecciones.soloVisibles(Set.of("leccion-dia-3"))))
                .thenReturn(List.of(new FragmentoRelevante("  El ritual de la manana empieza con agua.  ",
                        "leccion-dia-3", 0.1), new FragmentoRelevante("Despues, tierra y fuego.", null, 0.2)));

        ResultadoHerramienta resultado = buscar("  ritual de la manana pasos ");

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .startsWith("Material de los cursos para esta consulta")
                .contains("- El ritual de la manana empieza con agua.\n- Despues, tierra y fuego.");
        verify(vectorStorePort).buscarSimilares("ritual de la manana pasos", 5,
                FiltroLecciones.soloVisibles(Set.of("leccion-dia-3")));
    }

    @Test
    @DisplayName("sin nada en el material lo dice, para que SER no invente pasos")
    void sinResultadosLoDice() {
        when(visiblesPort.visiblesParaActor(APRENDIZ)).thenReturn(Set.of());
        when(vectorStorePort.buscarSimilares(anyString(), anyInt(), any())).thenReturn(List.of());

        assertThat(((ResultadoHerramienta.Exito) buscar("receta secreta")).contenido())
                .isEqualTo(BuscarEnLosCursosHerramienta.SIN_RESULTADOS);
    }

    @Test
    @DisplayName("si el embedding falla (cuota agotada) vuelve un fallo legible, nunca una excepcion")
    void unFalloDelEmbeddingNoRompeElTurno() {
        when(visiblesPort.visiblesParaActor(APRENDIZ)).thenReturn(Set.of("x"));
        when(vectorStorePort.buscarSimilares(anyString(), anyInt(), any()))
                .thenThrow(new IllegalStateException("429 de Gemini"));

        ResultadoHerramienta resultado = buscar("ritual");

        assertThat(resultado).isEqualTo(
                ResultadoHerramienta.fallo("No pude buscar en el material de los cursos en este momento."));
    }

    @Test
    @DisplayName("la consulta es obligatoria")
    void laConsultaEsObligatoria() {
        assertThat(herramienta.definicion().obligatoriosFaltantesEn(
                InvocacionHerramienta.sinArgumentos(BuscarEnLosCursosHerramienta.NOMBRE)))
                .containsExactly(BuscarEnLosCursosHerramienta.ARGUMENTO_CONSULTA);
    }
}
