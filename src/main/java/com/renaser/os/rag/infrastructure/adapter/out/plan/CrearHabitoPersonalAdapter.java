package com.renaser.os.rag.infrastructure.adapter.out.plan;

import com.renaser.os.habits.api.HabitosPersonalesPort;
import com.renaser.os.habits.api.HabitosPersonalesPort.HabitoPersonalNuevo;
import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Implementa {@link CrearHabitoPersonalPort} delegando en el contrato publico de {@code habits}
 * (D-41, D-229). Solo traduce: la dimension a su clave de {@code categorias_habito}.
 */
@Component
class CrearHabitoPersonalAdapter implements CrearHabitoPersonalPort {

    private final HabitosPersonalesPort habitosPersonales;

    CrearHabitoPersonalAdapter(HabitosPersonalesPort habitosPersonales) {
        this.habitosPersonales = habitosPersonales;
    }

    @Override
    public UUID crear(UserId actorId, HabitoPersonalPedido pedido) {
        return habitosPersonales.crear(actorId, new HabitoPersonalNuevo(pedido.nombre(),
                pedido.dimension().claveCategoria(), pedido.hora(), pedido.dias(), pedido.meta()));
    }

    @Override
    public LocalTime ultimaHoraDeInicio() {
        return HabitosPersonalesPort.ULTIMA_HORA_DE_DISPARO;
    }
}
