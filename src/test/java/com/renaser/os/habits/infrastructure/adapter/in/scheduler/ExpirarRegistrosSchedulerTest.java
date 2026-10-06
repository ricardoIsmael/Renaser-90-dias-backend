package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase.ResultadoDelBarrido;
import com.renaser.os.habits.application.ports.in.santuario.ExpirarRachasVencidasUseCase;
import com.renaser.os.habits.application.ports.out.participante.ListarParticipantesActivosPort;
import com.renaser.os.shared.domain.UserId;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * E-534: los registros se expiran cada hora (el dominio decide con quien) y las rachas sin celular siguen a las 05:00
 * UTC, cada uno con su cerrojo (C-5). Lo que hace cada corrida con datos reales lo prueba {@code ExpiracionPorZonaIT}.
 */
class ExpirarRegistrosSchedulerTest {

    @Test
    @DisplayName("registros: cada hora en el minuto 0 UTC, configurable, con cerrojo propio y metodo publico (proxy de ShedLock)")
    void registrosCadaHora() throws NoSuchMethodException {
        Method metodo = ExpirarRegistrosScheduler.class.getMethod("expirarRegistrosDeDiasTerminados");

        assertThat(metodo.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${renaser.scheduling.expirar-registros.cron:0 0 * * * *}");
        assertThat(metodo.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(metodo.getAnnotation(SchedulerLock.class).name()).isEqualTo("habits-expirar-registros");
        assertThat(Modifier.isPublic(metodo.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("rachas sin celular: a las 05:00 UTC como siempre, con su propio cerrojo")
    void rachasALasCincoUtc() throws NoSuchMethodException {
        Method metodo = ExpirarRegistrosScheduler.class.getMethod("expirarRachasSinCelularVencidas");

        assertThat(metodo.getAnnotation(Scheduled.class).cron()).isEqualTo("0 0 5 * * *");
        assertThat(metodo.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(metodo.getAnnotation(SchedulerLock.class).name()).isEqualTo("habits-expirar-rachas-sin-celular");
    }

    @Test
    @DisplayName("cada corrida hace lo suyo y solo lo suyo")
    void cadaCorridaHaceLoSuyo() {
        ExpirarRegistrosVencidosUseCase registros = mock(ExpirarRegistrosVencidosUseCase.class);
        ExpirarRachasVencidasUseCase rachas = mock(ExpirarRachasVencidasUseCase.class);
        ListarParticipantesActivosPort padron = mock(ListarParticipantesActivosPort.class);
        List<UserId> todos = List.of(UserId.of(UUID.randomUUID()));
        when(registros.expirarDiasTerminados()).thenReturn(new ResultadoDelBarrido(1, 1, 0));
        when(padron.todos()).thenReturn(todos);
        ExpirarRegistrosScheduler scheduler = new ExpirarRegistrosScheduler(registros, rachas, padron);

        scheduler.expirarRegistrosDeDiasTerminados();
        verify(registros).expirarDiasTerminados();
        verifyNoInteractions(rachas);

        scheduler.expirarRachasSinCelularVencidas();
        verify(rachas).expirarVencidas(todos);
    }
}
