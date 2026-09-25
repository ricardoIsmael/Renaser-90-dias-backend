package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E-247: un nombre de herramienta mal escrito por el modelo ya no tumba el turno. Antes Spring AI
 * lanzaba {@code No ToolCallback found for tool name: proponer_horario_por_dia_semana}.
 */
class ResolverDeHerramientasDesconocidasTest {

    private final EjecutarHerramientaAgenteUseCase herramientas = mock(EjecutarHerramientaAgenteUseCase.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EjecutarHerramientaAgenteUseCase> proveedor = mock(ObjectProvider.class);
    private final ResolverDeHerramientasDesconocidas resolver = new ResolverDeHerramientasDesconocidas(proveedor);

    private void conHerramientas(String... nombres) {
        when(proveedor.getIfAvailable()).thenReturn(herramientas);
        when(herramientas.disponibles(AgenteConversacional.COMPANION)).thenReturn(List.of(nombres).stream()
                .map(nombre -> DefinicionHerramienta.sinParametros(nombre, "descripcion")).toList());
    }

    @Test
    @DisplayName("el nombre que se comio el 'de' devuelve ok=false con el nombre correcto, sin ejecutar nada")
    void sugiereElNombreCorrecto() {
        conHerramientas("proponer_horario_por_dia_de_semana", "proponer_apagar_dia", "consultar_horarios");

        ToolCallback callback = resolver.resolve("proponer_horario_por_dia_semana");

        assertThat(callback.getToolDefinition().name()).isEqualTo("proponer_horario_por_dia_semana");
        assertThat(callback.call("{}")).contains("\"ok\":false")
                .contains("Quisiste decir 'proponer_horario_por_dia_de_semana'");
    }

    @Test
    @DisplayName("un nombre que no se parece a ninguno no sugiere nada, pero tampoco lanza")
    void sinSugerenciaSiNoSeParece() {
        conHerramientas("proponer_apagar_dia", "consultar_horarios");

        String respuesta = resolver.resolve("borrar_toda_la_base").call("{}");

        assertThat(respuesta).contains("\"ok\":false").contains("Usa solo los nombres exactos").doesNotContain("Quisiste");
    }

    @Test
    @DisplayName("si las herramientas todavia no estan listas, igual responde sin sugerencia")
    void sinCasoDeUso() {
        when(proveedor.getIfAvailable()).thenReturn(null);

        assertThat(resolver.resolve("consultar_horario").call("{}")).contains("\"ok\":false");
    }

    @Test
    @DisplayName("distancia de edicion: insertar 'de_' cuesta 3")
    void distancia() {
        assertThat(ResolverDeHerramientasDesconocidas.distancia("proponer_horario_por_dia_semana",
                "proponer_horario_por_dia_de_semana")).isEqualTo(3);
        assertThat(ResolverDeHerramientasDesconocidas.distancia("abc", "abc")).isZero();
    }

    @Test
    @DisplayName("con el resolver, el ToolCallingManager de Spring AI encuentra algo en vez de lanzar")
    void elManagerYaNoLanza() {
        conHerramientas("proponer_horario_por_dia_de_semana");
        var manager = DefaultToolCallingManager.builder().toolCallbackResolver(resolver).build();

        assertThat(manager).isNotNull();
        assertThat(resolver.resolve("proponer_horario_por_dia_semana")).isNotNull();
    }
}
