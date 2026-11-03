package com.renaser.os.rag.domain.model.herramienta;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que la app necesita saber cuando SER cumple un habito por una propuesta confirmada (spec 038, el caso del issue #33): cual registro y
 * cual habito, de que dia, y los puntos que pago el servidor. Con eso la app hace lo mismo que cuando se cumple desde Training: apaga las
 * alarmas de ese habito que le quedaban a ese dia, celebra y refresca el semaforo.
 *
 * <p>Es un dato de resultado, no de dominio: viaja dentro de {@link ResultadoHerramienta.Exito} y de la respuesta de
 * {@code POST /renasia/propuestas/{id}/confirmar}. Es aditivo: una app anterior lo ignora.
 *
 * @param registroId el registro del dia que se cerro
 * @param habitoId   el habito (catalogo o propio) del que es ese registro
 * @param fecha      el dia del habito (su {@code fechaEjecucion}, en la zona de la persona): puede no ser «hoy» del reloj
 * @param puntos     los puntos que pago el servidor (0 si ya no daba puntos)
 */
public record HabitoCumplido(UUID registroId, UUID habitoId, LocalDate fecha, int puntos) {

    public HabitoCumplido {
        Objects.requireNonNull(registroId, "registroId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
    }
}
