package com.renaser.os.users.infrastructure.config;

import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El plazo de gracia de las cuentas cerradas, leido una sola vez de la configuracion (D-243). */
@Configuration
class EliminacionDeCuentasConfig {

    @Bean
    PlazoDeGracia plazoDeGracia(@Value("${renaser.users.account-deletion.grace-period-days:30}") int dias) {
        return new PlazoDeGracia(dias);
    }
}
