package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.users.api.UserRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una persona de la lista de un grupo, como la ve el líder: quién es, su color de la semana y su porcentaje.
 *
 * @param rol          el rol de la cuenta: aprendiz, o mentor o guía con programa propio (spec 005, FR-002)
 * @param porcentaje   promedio de sus días medidos, 1 decimal; null si ninguno lo fue. Quien no se mide en el semáforo
 *                     ({@link ColorDeLaFila#NO_SE_MIDE}) lo lleva igual
 * @param diasConDatos cuántos días entraron a ese promedio
 * @param dias         los días de su ventana, para dibujar las barras; vacío si no tiene color (no se mide)
 * @param motivo       el nombre de {@code MotivoSinDatos} si está «Sin datos»; null si tiene color
 */
public record PersonaDeLaLista(UUID personaId, String nombre, String avatarUrl, UserRole rol, ColorDeLaFila color,
                               BigDecimal porcentaje, int diasConDatos, List<DiaDelSemaforo> dias, String motivo) {

    public PersonaDeLaLista {
        Objects.requireNonNull(personaId, "personaId es obligatorio");
        Objects.requireNonNull(color, "color es obligatorio");
        dias = dias == null ? List.of() : List.copyOf(dias);
    }
}
