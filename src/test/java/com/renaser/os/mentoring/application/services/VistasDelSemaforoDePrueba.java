package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Los servicios REALES de las vistas del semáforo, armados sobre los dobles de
 * {@link BancoDelSemaforo}, para las pruebas de los controllers ({@code @WebMvcTest}).
 *
 * <p>Por qué servicios reales y no mocks de los casos de uso: lo que hay que probar en la capa web es
 * que un MENTOR, un MENTOR_LEAD o un ADMIN suspendido reciben 403, y a esos roles el interceptor de
 * permisos los deja pasar (A-1, modo sombra). El 403 lo da el guard del servicio; con el caso de uso
 * mockeado la prueba solo comprobaría lo que el propio mock responde.
 *
 * <p>Vive en el paquete de los servicios porque el guard y el padrón son package-private. El
 * {@link UserSummaryFinder} de acá es también el que usa el interceptor de permisos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class VistasDelSemaforoDePrueba {

    @Bean
    BancoDelSemaforo bancoDelSemaforo() {
        return new BancoDelSemaforo();
    }

    @Bean
    AcompanamientoFinder acompanamientoFinder(BancoDelSemaforo banco) {
        return banco.acompanamiento;
    }

    @Bean
    SemaforoFinder semaforoFinder(BancoDelSemaforo banco) {
        return banco.semaforo;
    }

    @Bean
    UserSummaryFinder userSummaryFinder(BancoDelSemaforo banco) {
        return banco.usuarios;
    }

    @Bean
    Clock clock(BancoDelSemaforo banco) {
        return banco.reloj;
    }

    @Bean
    SemaforoDelGrupoService semaforoDelGrupoService(BancoDelSemaforo banco) {
        return banco.servicioDeTablas();
    }

    @Bean
    SemaforoDelAprendizService semaforoDelAprendizService(BancoDelSemaforo banco) {
        return banco.servicioDeDetalle();
    }

    @Bean
    SemaforoPorGruposService semaforoPorGruposService(BancoDelSemaforo banco) {
        return banco.servicioPorGrupos();
    }
}
