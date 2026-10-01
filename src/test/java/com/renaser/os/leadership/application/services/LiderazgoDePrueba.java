package com.renaser.os.leadership.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Los servicios REALES de la gestión del líder sobre los dobles de {@link BancoDeLiderazgo}, para las
 * pruebas del controller: el 403 de un MENTOR lo da el guard del servicio (el interceptor lo deja
 * pasar, A-1), así que mockear el caso de uso no probaría nada. El {@link UserSummaryFinder} de acá es
 * también el que usa el interceptor de permisos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LiderazgoDePrueba {

    @Bean
    BancoDeLiderazgo bancoDeLiderazgo() {
        return new BancoDeLiderazgo();
    }

    @Bean
    UserSummaryFinder userSummaryFinder(BancoDeLiderazgo banco) {
        return banco.usuariosFinder;
    }

    @Bean
    Clock clock(BancoDeLiderazgo banco) {
        return banco.reloj;
    }

    @Bean
    PadronDeMentoresService padronDeMentoresService(BancoDeLiderazgo banco) {
        return banco.padron();
    }

    @Bean
    FichaDeMentorService fichaDeMentorService(BancoDeLiderazgo banco) {
        return banco.ficha();
    }

    @Bean
    ObservacionesDeMentorService observacionesDeMentorService(BancoDeLiderazgo banco) {
        return banco.observacionesService();
    }

    @Bean
    ReporteDeMentoresService reporteDeMentoresService(BancoDeLiderazgo banco) {
        return banco.reporte();
    }
}
