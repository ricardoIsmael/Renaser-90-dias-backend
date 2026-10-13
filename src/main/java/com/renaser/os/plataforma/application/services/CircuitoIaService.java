package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.ConsultarCircuitoIaUseCase;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** El estado del circuito de la IA para la consola. Sin {@code rag} publicando su estado, la lista es vacía. */
@Service
class CircuitoIaService implements ConsultarCircuitoIaUseCase {

    private final GuardiaDeSoporteTi guardia;
    private final EstadoVigente estado;

    CircuitoIaService(GuardiaDeSoporteTi guardia, EstadoVigente estado) {
        this.guardia = guardia;
        this.estado = estado;
    }

    @Override
    public List<CircuitoAutomatico> circuitos(AccesoDeConsola acceso) {
        guardia.exigirConsolaAbierta(acceso);
        return estado.automaticos().stream().flatMap(automatico -> automatico.circuitos().stream())
                .filter(Objects::nonNull).toList();
    }
}
