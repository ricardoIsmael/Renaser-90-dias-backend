package com.renaser.os.habits.application.ports.in.medicion;

import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cierra el hábito de kilómetros con la lectura que hizo el teléfono de Health Connect (spec 037, D-310): el segundo camino
 * de {@code registros_habito.valor_medido}, junto al número que la persona escribe con su captura
 * ({@code POST /habit-tracks/{id}/complete}). Termina en el mismo {@code CompletarRegistroUseCase}: mismos puntos, misma
 * ventana, mismo evento y misma regla del día cerrado (D-259).
 *
 * <p><b>Idempotente:</b> si el registro ya quedó cumplido por Health Connect devuelve ese registro sin pagar otra vez.
 */
public interface RegistrarMedicionAutomaticaUseCase {

    RegistroHabito registrar(RegistrarMedicionAutomaticaCommand command);

    /**
     * @param actorId     tiene que ser el dueño del registro: nadie registra km de otro
     * @param kilometros  los km del día que calculó el teléfono (distancia agregada o pasos por zancada)
     * @param pasos       opcional: un reloj puede dar solo la distancia
     * @param fuente      etiqueta que lee el mentor («Galaxy Watch», «Tu teléfono»); no decide nada
     * @param leidoEn     cuándo leyó el teléfono
     */
    record RegistrarMedicionAutomaticaCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                              @NotNull BigDecimal kilometros, Integer pasos, @NotBlank String fuente,
                                              @NotNull DispositivoDeMedicion dispositivo, @NotNull Instant leidoEn) {

        public RegistrarMedicionAutomaticaCommand {
            SelfValidating.validateConstructorArgs(RegistrarMedicionAutomaticaCommand.class, actorId, registroId,
                    kilometros, pasos, fuente, dispositivo, leidoEn);
        }
    }
}
