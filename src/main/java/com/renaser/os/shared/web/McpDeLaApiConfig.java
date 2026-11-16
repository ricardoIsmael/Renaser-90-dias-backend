package com.renaser.os.shared.web;

import io.swagger.v3.oas.models.PathItem;
import org.springdoc.ai.customizers.McpToolCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP de la API (springdoc 3.2, «MCP Support»): publica las operaciones de {@code /api/v1/**}
 * como herramientas para que un agente de IA (Claude Code, Cursor…) CONOZCA la API — qué rutas
 * hay, qué reciben y qué devuelven — sin leer los ~170 controllers.
 *
 * <p><b>Solo local.</b> {@code /mcp}, {@code /mcp-ui} y {@code /api/mcp-admin/**} no traen
 * autenticacion propia (lo dice la documentacion de springdoc) y viven fuera del filtro de
 * {@link SecurityConfig}; application-prod.yaml los apaga ({@code MCP_HABILITADO=false}).
 *
 * <p><b>Solo lectura.</b> Lo que no es GET ni se publica como herramienta: el objetivo es que la
 * IA conozca la API, no que cree, cambie o borre datos. (La "aprobacion humana" que springdoc trae
 * para las escrituras es una confirmacion, no un control de acceso: el mismo cliente puede
 * aprobarse solo.) Para leer datos protegidos, la llamada necesita una sesion local
 * ({@code X-Auth-Token}); sin ella el servidor responde 401, como a cualquiera.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.ai.mcp.enabled", havingValue = "true")
public class McpDeLaApiConfig {

    @Bean
    McpToolCustomizer soloLecturaParaLaIa() {
        return (contexto, ruta, metodo, operacion) -> {
            if (!esLectura(metodo)) contexto.setExclude(true);
            return contexto;
        };
    }

    static boolean esLectura(PathItem.HttpMethod metodo) {
        return metodo == PathItem.HttpMethod.GET;
    }
}
