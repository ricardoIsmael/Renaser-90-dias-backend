package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.AvisarRojosDelDiaUseCase;
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

/** El cerrojo, el horario y el interruptor del aviso de rojo de las 19:00 (spec 057, FR-031; C-5). */
class AvisarRojosDelDiaSchedulerTest {

    @Test
    @DisplayName("spec 057 FR-031: cada 30 minutos (minutos 0 y 30 UTC), configurable, en UTC, con cerrojo propio y método público (proxy de ShedLock)")
    void cadaMediaHoraConCerrojo() throws NoSuchMethodException { // spec 057 FR-031
        Method metodo = AvisarRojosDelDiaScheduler.class.getMethod("ejecutar");

        assertThat(metodo.getAnnotation(Scheduled.class).cron()).isEqualTo("${renaser.scheduling.aviso-de-rojo.cron:0 0,30 * * * *}");
        assertThat(metodo.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(metodo.getAnnotation(SchedulerLock.class).name()).isEqualTo("mentoring-aviso-de-rojo");
        assertThat(Modifier.isPublic(metodo.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("spec 057 FR-031: cada ventana local de 19:00 a 19:29 tiene UNA corrida, en cualquier zona (Lima, Madrid, Kolkata +5:30, Katmandú +5:45)")
    void unaCorridaPorVentanaLocal() throws NoSuchMethodException { // spec 057 FR-031
        String cron = AvisarRojosDelDiaScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class).cron()
                .replace("${renaser.scheduling.aviso-de-rojo.cron:", "").replace("}", "");
        CronExpression expresion = CronExpression.parse(cron);

        for (String zona : List.of("America/Lima", "Europe/Madrid", "Asia/Kolkata", "Asia/Kathmandu")) {
            ZoneId id = ZoneId.of(zona);
            ZonedDateTime abre = LocalDate.of(2026, 11, 20).atTime(19, 0).atZone(id);
            ZonedDateTime cierra = abre.plusMinutes(30);
            List<ZonedDateTime> corridas = new ArrayList<>();
            ZonedDateTime corrida = expresion.next(abre.minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC));
            while (corrida != null && corrida.isBefore(cierra)) {
                corridas.add(corrida);
                corrida = expresion.next(corrida);
            }
            assertThat(corridas).as(zona).hasSize(1);
        }
    }

    @Test
    @DisplayName("spec 057 FR-031: se puede apagar por propiedad, y la suite lo apaga para que no dispare en medio de otra prueba")
    void seApagaPorPropiedad() { // spec 057 FR-031
        ConditionalOnProperty condicion = AvisarRojosDelDiaScheduler.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condicion.name()).containsExactly("renaser.scheduling.aviso-de-rojo.activo");
        assertThat(condicion.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("spec 057 FR-031: con la funcionalidad mentor.mi_grupo apagada por Soporte TI no corre este turno; prendida, avisa")
    void elInterruptor() { // spec 057 FR-031
        AvisarRojosDelDiaUseCase avisar = mock(AvisarRojosDelDiaUseCase.class);
        Interruptores apagada = mock(Interruptores.class);
        when(apagada.prendida("mentor.mi_grupo")).thenReturn(false);
        new AvisarRojosDelDiaScheduler(avisar, apagada).ejecutar();
        verifyNoInteractions(avisar);

        Interruptores prendida = mock(Interruptores.class);
        when(prendida.prendida("mentor.mi_grupo")).thenReturn(true);
        new AvisarRojosDelDiaScheduler(avisar, prendida).ejecutar();
        verify(avisar).avisar();
    }
}
