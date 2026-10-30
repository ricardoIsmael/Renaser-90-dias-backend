package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.CambioDeDia;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * {@code PUT /api/v1/radar/mi-radar} (spec 019). {@code encendido} rige en el acto; {@code dias} son SOLO los dias de la
 * semana que se tocan (lunes = 1 ... domingo = 7) y lo que cada uno no dice se queda como estaba. {@code modo} es
 * {@code AUTOMATICO} o {@code PERSONALIZADO}; {@code veces}, de 1 a 21 y solo cuenta en Personalizado; {@code sinRadar}
 * apaga ese dia. El dominio valida el resto (400) y rechaza con 409 cambiar hoy o un dia pasado.
 */
public record GuardarMiRadarRequest(@NotNull Boolean encendido, List<@NotNull @Valid DiaDeMiRadarRequest> dias) {

    public record DiaDeMiRadarRequest(@NotNull Integer dia, Modo modo, Integer veces, Boolean sinRadar) {
    }

    /** Los dias como los entiende el caso de uso; vacia si el cuerpo no trae {@code dias}. */
    public List<CambioDeDia> cambios() {
        return dias == null ? List.of() : dias.stream()
                .map(propio -> new CambioDeDia(propio.dia(), propio.modo(), propio.veces(), propio.sinRadar())).toList();
    }
}
