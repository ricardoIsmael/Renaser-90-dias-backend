package com.renaser.os.shared.web;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.ai.customizers.McpToolDefinitionContext;

import static org.assertj.core.api.Assertions.assertThat;

/** El MCP local es de solo lectura: la IA conoce la API, no la modifica. */
class McpDeLaApiConfigTest {

    private static McpToolDefinitionContext procesar(HttpMethod metodo) {
        McpToolDefinitionContext contexto = new McpToolDefinitionContext("herramienta", "descripcion", "{}");
        return new McpDeLaApiConfig().soloLecturaParaLaIa().customize(contexto, "/api/v1/home", metodo, new Operation());
    }

    @Test
    @DisplayName("un GET queda como herramienta")
    void getSePublica() {
        assertThat(procesar(HttpMethod.GET).isExclude()).isFalse();
    }

    @Test
    @DisplayName("POST, PUT, PATCH y DELETE no se publican")
    void escriturasFuera() {
        for (HttpMethod m : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            assertThat(procesar(m).isExclude()).as(m.name()).isTrue();
        }
    }
}
