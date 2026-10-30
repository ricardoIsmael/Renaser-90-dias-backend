package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase;
import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase.ResultadoDelCierre;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 015, FR-018 y constitución V: el barrido de la foto semanal corre CADA HORA (el domingo de cada persona termina a
 * una hora UTC distinta según su zona, y quien decide con quién hay algo que hacer es el servicio), con cerrojo propio.
 * Lo que guarda con datos reales lo prueba {@code CierreDeHuecosIT}.
 */
class CerrarHuecosSchedulerTest {

    @Test
    @DisplayName("cada hora en el minuto 50 UTC, configurable, con cerrojo propio y metodo publico (proxy de ShedLock)")
    void cadaHoraConCerrojo() throws NoSuchMethodException {
        Method metodo = CerrarHuecosScheduler.class.getMethod("cerrarPendientes");

        assertThat(metodo.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${renaser.scheduling.huecos-cierre.cron:0 50 * * * *}");
        assertThat(metodo.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(metodo.getAnnotation(SchedulerLock.class).name()).isEqualTo("mentoring-cerrar-huecos");
        assertThat(Modifier.isPublic(metodo.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("cada corrida llama al caso de uso una vez y no revienta aunque haya personas fallidas")
    void delegaEnElCasoDeUso() {
        CerrarHuecosDeLaSemanaUseCase cierre = mock(CerrarHuecosDeLaSemanaUseCase.class);
        when(cierre.cerrarPendientes()).thenReturn(new ResultadoDelCierre(10, 8, 2));

        new CerrarHuecosScheduler(cierre).cerrarPendientes();

        verify(cierre).cerrarPendientes();
    }
}
