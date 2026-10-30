package com.renaser.os.mentoring.domain.model.huecos;

import java.util.List;
import java.util.Objects;

/**
 * Lo que un día tiene de más cuando se CALCULA al pedirlo y que la foto semanal no guarda (spec 015, corregido
 * 2026-10-29): cada bloque ocupado con su nombre, cuántos hábitos y acciones del día se cumplieron a tiempo, tarde o
 * no, y —si el día cuenta entero— por qué.
 *
 * <p><b>No se persiste.</b> {@code huecos_semana} (V120) guarda tramos de tiempo y no etiquetas: ni el título de un
 * hábito ni el de un evento. Una semana ya cerrada sale de la foto, así que sus días no traen detalle; quien los
 * muestra tiene que tolerarlo.
 *
 * @param bloques    los ocupados recortados a la ventana del día, en orden y sin unir
 * @param real       lo cumplido de ese día (cero en un día que todavía no llega)
 * @param sinVentana {@code null} si el día va de Despertar a Dormir
 */
public record DetalleDelDia(List<BloqueOcupado> bloques, LoReal real, MotivoSinVentana sinVentana) {

    public DetalleDelDia {
        bloques = List.copyOf(bloques);
        Objects.requireNonNull(real, "real es obligatorio");
    }

    public DetalleDelDia conLoReal(LoReal nuevo) {
        return new DetalleDelDia(bloques, nuevo, sinVentana);
    }
}
