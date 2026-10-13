package com.renaser.os.shared.infrastructure.interruptores;

import com.renaser.os.shared.application.AuditoriaDePlataforma;
import com.renaser.os.shared.application.Interruptores;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los respaldos de las piezas de {@code plataforma} que otros módulos usan (contrato §1): sin ese módulo en el
 * contexto, todo prendido y la auditoría automática no anota nada. En la aplicación completa {@code plataforma}
 * declara las suyas y estas no se crean ({@code @ConditionalOnMissingBean}: las clases escaneadas ya están
 * registradas cuando se evalúan los {@code @Bean} de esta configuración; además las de {@code plataforma} son
 * {@code @Primary}, por si alguna vez se evaluaran antes).
 */
@Configuration(proxyBeanMethods = false)
class InterruptoresPorDefectoConfig {

    @Bean
    @ConditionalOnMissingBean(Interruptores.class)
    Interruptores todoPrendido() {
        return new TodoPrendido();
    }

    @Bean
    @ConditionalOnMissingBean(AuditoriaDePlataforma.class)
    AuditoriaDePlataforma auditoriaSinPlataforma() {
        return (clave, antes, despues, causa) -> {
            // Sin plataforma no hay dónde anotar.
        };
    }
}
