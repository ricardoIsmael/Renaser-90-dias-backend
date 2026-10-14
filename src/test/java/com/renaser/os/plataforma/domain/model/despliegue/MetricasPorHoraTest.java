package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.Conteo;
import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.MetricaDeLaHora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las 24 h del gráfico (contrato fase 2 §3.4, P5). Sin Spring. Reloj en la madrugada UTC (regla 02). */
class MetricasPorHoraTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T03:41:12Z");

    @Test
    @DisplayName("siempre 24 horas, de la más vieja a la actual, en UTC; las que no tienen datos en 0")
    void veinticuatroHoras() {
        List<MetricaDeLaHora> serie = MetricasPorHora.ultimas24Horas(AHORA, List.of());

        assertThat(serie).hasSize(24);
        assertThat(serie.getFirst().hora()).isEqualTo(Instant.parse("2026-10-13T04:00:00Z"));
        assertThat(serie.getLast().hora()).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
        assertThat(serie).allSatisfy(hora -> {
            assertThat(hora.dentro()).isEqualTo(Conteo.CERO);
            assertThat(hora.fuera()).isEqualTo(Conteo.CERO);
        });
    }

    @Test
    @DisplayName("separa dentro y fuera por hora e ignora lo que cae fuera de la ventana")
    void separaDentroYFuera() {
        Instant estaHora = Instant.parse("2026-10-14T03:00:00Z");
        List<ConteoDeDespliegue> filas = List.of(
                new ConteoDeDespliegue("eventos", estaHora, true, 40, 2),
                new ConteoDeDespliegue("eventos", estaHora, false, 12, 0),
                new ConteoDeDespliegue("eventos", Instant.parse("2026-10-13T20:00:00Z"), true, 5, 1),
                new ConteoDeDespliegue("eventos", Instant.parse("2026-10-13T03:00:00Z"), true, 99, 9));

        List<MetricaDeLaHora> serie = MetricasPorHora.ultimas24Horas(AHORA, filas);

        assertThat(serie.getLast().dentro()).isEqualTo(new Conteo(40, 2));
        assertThat(serie.getLast().fuera()).isEqualTo(new Conteo(12, 0));
        assertThat(serie.get(16).hora()).isEqualTo(Instant.parse("2026-10-13T20:00:00Z"));
        assertThat(serie.get(16).dentro()).isEqualTo(new Conteo(5, 1));
        assertThat(serie.stream().mapToLong(hora -> hora.dentro().pedidos()).sum())
                .as("la fila de hace 24 h ya no entra").isEqualTo(45);
    }

    @Test
    @DisplayName("una fila va truncada a la hora y un error también es un pedido")
    void invariantesDeLaFila() {
        assertThatThrownBy(() -> new ConteoDeDespliegue("eventos", AHORA, true, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteoDeDespliegue("eventos", ConteoDeDespliegue.horaDe(AHORA), true, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ConteoDeDespliegue.horaDe(AHORA)).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
    }
}
