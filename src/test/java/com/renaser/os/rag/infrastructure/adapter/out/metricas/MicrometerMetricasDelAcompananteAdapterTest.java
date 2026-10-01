package com.renaser.os.rag.infrastructure.adapter.out.metricas;

import com.renaser.os.rag.application.ports.out.metricas.RegistrarMetricaDelAcompanantePort.AperturaDeVoz;
import com.renaser.os.rag.application.ports.out.metricas.RegistrarMetricaDelAcompanantePort.ResolucionDePropuesta;
import com.renaser.os.rag.application.ports.out.metricas.RegistrarMetricaDelAcompanantePort.ResultadoDelTurno;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** D-237: nombres y etiquetas que leen los paneles de Grafana (infra/observabilidad/grafana). */
class MicrometerMetricasDelAcompananteAdapterTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MicrometerMetricasDelAcompananteAdapter adapter = new MicrometerMetricasDelAcompananteAdapter(registro);

    @Test
    @DisplayName("un turno cuenta el mensaje y su duracion, con agente y resultado en minusculas")
    void turno() {
        adapter.turnoDelChat(AgenteConversacional.COMPANION, ResultadoDelTurno.OK, Duration.ofSeconds(3));
        adapter.turnoRechazadoPorLimite(AgenteConversacional.COMPANION);

        assertThat(registro.get("renaser.acompanante.mensajes").tag("agente", "companion").tag("resultado", "ok")
                .counter().count()).isEqualTo(1.0);
        assertThat(registro.get("renaser.acompanante.mensajes").tag("resultado", "limite_diario")
                .counter().count()).isEqualTo(1.0);
        assertThat(registro.get("renaser.acompanante.turnos").tag("resultado", "ok").timer()
                .totalTime(TimeUnit.SECONDS)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("propuestas por herramienta; un nombre raro no abre una serie nueva")
    void propuestas() {
        adapter.propuestaCreada("proponer_renombrar_habito");
        adapter.propuestaResuelta("proponer_renombrar_habito", ResolucionDePropuesta.CONFIRMADA);
        adapter.propuestaCreada("Algo con espacios y 'comillas'");

        assertThat(registro.get("renaser.acompanante.propuestas.creadas")
                .tag("herramienta", "proponer_renombrar_habito").counter().count()).isEqualTo(1.0);
        assertThat(registro.get("renaser.acompanante.propuestas.creadas").tag("herramienta", "otra")
                .counter().count()).isEqualTo(1.0);
        assertThat(registro.get("renaser.acompanante.propuestas.resueltas").tag("resolucion", "confirmada")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("voz en vivo: aperturas por resultado y segundos hablados")
    void voz() {
        adapter.aperturaDeVozEnVivo(AperturaDeVoz.ABIERTA);
        adapter.tiempoDeVozEnVivo(Duration.ofMillis(7_500));
        adapter.tiempoDeVozEnVivo(Duration.ofSeconds(2));

        assertThat(registro.get("renaser.voz.en.vivo.aperturas").tag("resultado", "abierta").counter().count())
                .isEqualTo(1.0);
        assertThat(registro.get("renaser.voz.en.vivo.hablado").counter().count()).isEqualTo(9.5);
    }
}
