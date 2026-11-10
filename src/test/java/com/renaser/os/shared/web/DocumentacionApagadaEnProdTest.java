package com.renaser.os.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Swagger y el MCP viven fuera de /api/v1/**: SecurityConfig no los protege. En prod nacen
 * apagados (docs/API_OPENAPI.md, D-336). Si alguien borra esas lineas, esta prueba lo dice. Se leen
 * los archivos por ruta: el application.yaml de test REEMPLAZA al de main en el classpath.
 */
class DocumentacionApagadaEnProdTest {

    private static Object enProd(String clave) throws Exception {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("prod", new FileSystemResource("src/main/resources/application-prod.yaml"));
        return fuentes.stream().map(f -> f.getProperty(clave)).filter(v -> v != null).findFirst().orElse(null);
    }

    private static Object enBase(String clave) throws Exception {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("base", new FileSystemResource("src/main/resources/application.yaml"));
        return fuentes.stream().map(f -> f.getProperty(clave)).filter(v -> v != null).findFirst().orElse(null);
    }

    @Test
    @DisplayName("prod apaga Swagger (/v3/api-docs, /swagger-ui) y el MCP (/mcp, /mcp-ui)")
    void prodApagaTodo() throws Exception {
        assertThat(String.valueOf(enProd("OPENAPI_HABILITADO"))).isEqualTo("false");
        assertThat(String.valueOf(enProd("MCP_HABILITADO"))).isEqualTo("false");
    }

    @Test
    @DisplayName("los cinco interruptores leen esas dos variables")
    void losInterruptoresLeenLasVariables() throws Exception {
        assertThat(String.valueOf(enBase("springdoc.api-docs.enabled"))).contains("OPENAPI_HABILITADO");
        assertThat(String.valueOf(enBase("springdoc.swagger-ui.enabled"))).contains("OPENAPI_HABILITADO");
        assertThat(String.valueOf(enBase("springdoc.ai.mcp.enabled"))).contains("MCP_HABILITADO");
        assertThat(String.valueOf(enBase("springdoc.ai.mcp.dashboard-enabled"))).contains("MCP_HABILITADO");
        assertThat(String.valueOf(enBase("spring.ai.mcp.server.enabled"))).contains("MCP_HABILITADO");
    }
}
