package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.metricas.RegistrarMetricaDelAcompanantePort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Las metricas del acompanante que registraron los servicios, como texto, para las pruebas (D-237). */
public class MetricasDelAcompananteEnMemoria implements RegistrarMetricaDelAcompanantePort {

    public final List<String> registradas = new ArrayList<>();
    public Duration hablado = Duration.ZERO;

    @Override
    public void turnoDelChat(AgenteConversacional agente, ResultadoDelTurno resultado, Duration duracion) {
        registradas.add("turno " + agente + " " + resultado);
    }

    @Override
    public void turnoRechazadoPorLimite(AgenteConversacional agente) {
        registradas.add("limite " + agente);
    }

    @Override
    public void propuestaCreada(String herramienta) {
        registradas.add("creada " + herramienta);
    }

    @Override
    public void propuestaResuelta(String herramienta, ResolucionDePropuesta resolucion) {
        registradas.add(resolucion + " " + herramienta);
    }

    @Override
    public void aperturaDeVozEnVivo(AperturaDeVoz apertura) {
        registradas.add("voz " + apertura);
    }

    @Override
    public void tiempoDeVozEnVivo(Duration tramo) {
        hablado = hablado.plus(tramo);
    }
}
