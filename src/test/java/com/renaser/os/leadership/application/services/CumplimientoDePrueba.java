package com.renaser.os.leadership.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Los servicios REALES de la ficha del líder sobre los dobles de {@link BancoDeCumplimiento}, para las pruebas del
 * controller: el 403 de un MENTOR lo da el guard del servicio (el interceptor lo deja pasar, A-1), así que mockear
 * el caso de uso no probaría nada. El {@link UserSummaryFinder} de acá es también el que usa el interceptor de
 * permisos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CumplimientoDePrueba {

    @Bean
    BancoDeCumplimiento bancoDeCumplimiento() {
        return new BancoDeCumplimiento();
    }

    @Bean
    UserSummaryFinder userSummaryFinder(BancoDeCumplimiento banco) {
        return banco.usuariosFinder;
    }

    @Bean
    Clock clock(BancoDeCumplimiento banco) {
        return banco.reloj;
    }

    @Bean
    GrupoDelLiderService grupoDelLiderService(BancoDeCumplimiento banco) {
        return banco.grupoService();
    }

    @Bean
    SemanaDePersonaService semanaDePersonaService(BancoDeCumplimiento banco) {
        return banco.semanaService();
    }

    @Bean
    RadarDePersonaService radarDePersonaService(BancoDeCumplimiento banco) {
        return banco.radarService();
    }

    @Bean
    PersonasDelProgramaService personasDelProgramaService(BancoDeCumplimiento banco) {
        return banco.personasDelProgramaService();
    }

    @Bean
    HuecosDePersonaService huecosDePersonaService(BancoDeCumplimiento banco) {
        return banco.huecosService();
    }

    @Bean
    CierresDePersonaService cierresDePersonaService(BancoDeCumplimiento banco) {
        return banco.cierresService();
    }
}
