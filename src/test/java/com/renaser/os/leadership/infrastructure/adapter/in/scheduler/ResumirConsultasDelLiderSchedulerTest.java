package com.renaser.os.leadership.infrastructure.adapter.in.scheduler;

import com.renaser.os.leadership.application.ports.in.ResumirConsultasDelLiderUseCase;
import com.renaser.os.shared.application.Interruptores;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El cerrojo, el horario y el interruptor del resumen «Tu líder revisó a tu grupo» (spec 005, C-5). Lo que hace cada
 * corrida con datos reales lo prueba {@code ResumenDeConsultasServiceTest}; que no avise dos veces con la base,
 * {@code ResumirConsultasDelLiderSchedulerIT}.
 */
class ResumirConsultasDelLiderSchedulerTest {

    @Test
    @DisplayName("dos veces por hora, en los minutos 10 y 40 UTC, configurable, con cerrojo propio y metodo publico (proxy de ShedLock)")
    void cadaHoraConCerrojo() throws NoSuchMethodException {
        Method metodo = ResumirConsultasDelLiderScheduler.class.getMethod("ejecutar");

        assertThat(metodo.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${renaser.scheduling.resumen-consultas-lider.cron:0 10,40 * * * *}");
        assertThat(metodo.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(metodo.getAnnotation(SchedulerLock.class).name()).isEqualTo("leadership-resumir-consultas");
        assertThat(Modifier.isPublic(metodo.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("cada ventana local de 20:00 a 20:59 tiene DOS intentos, en cualquier zona (Lima, Madrid, Kolkata +5:30, Katmandu +5:45)")
    void dosIntentosPorVentanaLocal() throws NoSuchMethodException {
        String cron = ResumirConsultasDelLiderScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class).cron()
                .replace("${renaser.scheduling.resumen-consultas-lider.cron:", "").replace("}", "");
        CronExpression expresion = CronExpression.parse(cron);

        for (String zona : List.of("America/Lima", "Europe/Madrid", "Asia/Kolkata", "Asia/Kathmandu")) {
            ZoneId id = ZoneId.of(zona);
            ZonedDateTime abre = LocalDate.of(2026, 10, 18).atTime(20, 0).atZone(id);
            ZonedDateTime cierra = abre.plusHours(1);
            List<ZonedDateTime> corridas = new ArrayList<>();
            ZonedDateTime corrida = expresion.next(abre.minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC));
            while (corrida != null && corrida.isBefore(cierra)) {
                corridas.add(corrida);
                corrida = expresion.next(corrida);
            }
            assertThat(corridas).as(zona).hasSize(2);
        }
    }

    @Test
    @DisplayName("se puede apagar por propiedad, y la suite lo apaga para que no dispare en medio de otra prueba")
    void seApagaPorPropiedad() {
        ConditionalOnProperty condicion = ResumirConsultasDelLiderScheduler.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condicion.name()).containsExactly("renaser.scheduling.resumen-consultas-lider.activo");
        assertThat(condicion.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("con la funcionalidad lider.cumplimiento apagada por Soporte TI no corre este turno")
    void apagadaNoCorre() {
        ResumirConsultasDelLiderUseCase resumir = mock(ResumirConsultasDelLiderUseCase.class);
        Interruptores interruptores = mock(Interruptores.class);
        when(interruptores.prendida("lider.cumplimiento")).thenReturn(false);

        new ResumirConsultasDelLiderScheduler(resumir, interruptores).ejecutar();

        verifyNoInteractions(resumir);
    }

    @Test
    @DisplayName("con la funcionalidad prendida resume")
    void prendidaCorre() {
        ResumirConsultasDelLiderUseCase resumir = mock(ResumirConsultasDelLiderUseCase.class);
        Interruptores interruptores = mock(Interruptores.class);
        when(interruptores.prendida("lider.cumplimiento")).thenReturn(true);

        new ResumirConsultasDelLiderScheduler(resumir, interruptores).ejecutar();

        verify(resumir).resumir();
    }
}
