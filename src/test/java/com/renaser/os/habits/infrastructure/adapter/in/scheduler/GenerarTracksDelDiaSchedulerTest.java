package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase.ResultadoDelBarrido;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El barrido solo delega: la decision de a quien le toca y como se aisla cada participante vive en
 * {@code GeneracionDeJornadasServiceTest}. Aca se fija lo que es del adaptador: que corra CADA HORA (E-556) y con lock.
 */
class GenerarTracksDelDiaSchedulerTest {

    @Test
    @DisplayName("ejecutar(): delega en el caso de uso, una vez por corrida")
    void delegaEnElCasoDeUso() {
        GenerarJornadasDelDiaUseCase casoDeUso = mock(GenerarJornadasDelDiaUseCase.class);
        when(casoDeUso.generarLasQueYaEmpezaron()).thenReturn(new ResultadoDelBarrido(3, 1, 0));

        new GenerarTracksDelDiaScheduler(casoDeUso, new com.renaser.os.shared.infrastructure.interruptores.TodoPrendido()).ejecutar();

        verify(casoDeUso).generarLasQueYaEmpezaron();
    }

    @Test
    @DisplayName("spec 002: con los hábitos apagados no arma el día; el barrido de la hora siguiente se pone al día")
    void conLosHabitosApagadosNoArmaElDia() {
        GenerarJornadasDelDiaUseCase casoDeUso = mock(GenerarJornadasDelDiaUseCase.class);

        new GenerarTracksDelDiaScheduler(casoDeUso,
                com.renaser.os.shared.application.InterruptoresDePrueba.conApagadas("habitos")).ejecutar();

        org.mockito.Mockito.verifyNoInteractions(casoDeUso);
    }

    @Test
    @DisplayName("el cron por defecto corre cada hora, en el minuto 2 (E-556): a las 05:02 UTC, que es Lima, y a las otras 23")
    void correCadaHora() throws NoSuchMethodException {
        Scheduled programado = GenerarTracksDelDiaScheduler.class.getDeclaredMethod("ejecutar")
                .getAnnotation(Scheduled.class);
        String porDefecto = programado.cron().substring(programado.cron().indexOf(':') + 1,
                programado.cron().length() - 1);
        CronExpression cron = CronExpression.parse(porDefecto);

        ZonedDateTime disparo = ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        for (int hora = 0; hora < 24; hora++) {
            disparo = cron.next(disparo);
            assertThat(disparo).as("la corrida %d del dia", hora)
                    .isEqualTo(ZonedDateTime.of(2026, 11, 9, hora, 2, 0, 0, ZoneOffset.UTC));
        }
        assertThat(cron.next(ZonedDateTime.of(2026, 11, 9, 5, 0, 0, 0, ZoneOffset.UTC)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 9, 5, 2, 0, 0, ZoneOffset.UTC));
        assertThat(cron.next(ZonedDateTime.of(2026, 11, 9, 5, 2, 0, 0, ZoneOffset.UTC)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 9, 6, 2, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("sigue con su @SchedulerLock (C-5), con el mismo nombre de siempre")
    void conservaElLock() throws NoSuchMethodException {
        SchedulerLock lock = GenerarTracksDelDiaScheduler.class.getDeclaredMethod("ejecutar")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock.name()).isEqualTo("habits-generar-tracks-del-dia");
    }
}
