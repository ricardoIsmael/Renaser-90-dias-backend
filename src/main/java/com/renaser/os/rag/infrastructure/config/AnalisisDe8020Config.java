package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Los números del análisis 80/20 (spec 031, «Supuestos»), por propiedad {@code renaser.analisis-80-20.*}. Lo que no se configura toma el
 * valor propuesto en la spec ({@link ReglasDelAnalisis#POR_DEFECTO}); se cambian sin tocar código. Un valor inválido hace fallar el
 * arranque con el motivo.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalisisDe8020Config.Propiedades.class)
class AnalisisDe8020Config {

    @Bean
    ReglasDelAnalisis reglasDelAnalisis(Propiedades propiedades) {
        ReglasDelAnalisis defecto = ReglasDelAnalisis.POR_DEFECTO;
        return new ReglasDelAnalisis(
                valor(propiedades.minimoDeAccionesPorSemana(), defecto.minimoDeAccionesPorSemana()),
                valor(propiedades.minimoDeDiasConAcciones(), defecto.minimoDeDiasConAcciones()),
                valor(propiedades.minimoDeAccionesPorMes(), defecto.minimoDeAccionesPorMes()),
                valor(propiedades.minimoDeRespuestasDelRadar(), defecto.minimoDeRespuestasDelRadar()),
                valor(propiedades.maximoDeIntentos(), defecto.maximoDeIntentos()),
                propiedades.esperaEntreIntentos() == null ? defecto.esperaEntreIntentos() : propiedades.esperaEntreIntentos(),
                valor(propiedades.diasDePuestaAlDia(), defecto.diasDePuestaAlDia()),
                valor(propiedades.generacionesPorDia(), defecto.generacionesPorDia()));
    }

    private static int valor(Integer configurado, int defecto) {
        return configurado == null ? defecto : configurado;
    }

    @ConfigurationProperties(prefix = "renaser.analisis-80-20")
    record Propiedades(Integer minimoDeAccionesPorSemana, Integer minimoDeDiasConAcciones, Integer minimoDeAccionesPorMes,
                       Integer minimoDeRespuestasDelRadar, Integer maximoDeIntentos, Duration esperaEntreIntentos,
                       Integer diasDePuestaAlDia, Integer generacionesPorDia) {
    }
}
