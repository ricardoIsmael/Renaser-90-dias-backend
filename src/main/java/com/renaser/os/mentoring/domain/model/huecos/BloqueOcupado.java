package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Un pedazo ocupado de un día, TAL COMO lo planificó la persona: un hábito, una acción, un evento o un tramo de su
 * agenda personal. A diferencia de {@link DiaDeHuecos#ocupados()}, que es la unión de todos (los que se cruzan son un
 * solo bloque y no dicen de quién son), este conserva su nombre para que la agenda del día se lea como una agenda
 * (spec 015, corregido 2026-10-29).
 *
 * <p>Los que se cruzan se entregan por separado y quien dibuja decide cómo acomodarlos. La agenda personal nunca lleva
 * título: la persona la dijo como «estoy ocupada de tal a tal hora», sin etiqueta, y así se queda.
 *
 * @param titulo {@code null} para la agenda y para quien no tiene nombre
 */
public record BloqueOcupado(TramoDelDia tramo, TipoDeOcupado tipo, String titulo) {

    /** Por hora de inicio, luego de fin, luego tipo y nombre: el mismo orden aunque lleguen en otro. */
    public static final Comparator<BloqueOcupado> EN_ORDEN = Comparator
            .comparingInt((BloqueOcupado bloque) -> bloque.tramo().desde())
            .thenComparingInt(bloque -> bloque.tramo().hasta())
            .thenComparing(BloqueOcupado::tipo)
            .thenComparing(BloqueOcupado::titulo, Comparator.nullsFirst(Comparator.naturalOrder()));

    public BloqueOcupado {
        Objects.requireNonNull(tramo, "tramo es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        titulo = tipo == TipoDeOcupado.AGENDA || titulo == null || titulo.isBlank() ? null : titulo.trim();
    }

    public static BloqueOcupado deAgenda(TramoDelDia tramo) {
        return new BloqueOcupado(tramo, TipoDeOcupado.AGENDA, null);
    }

    /** La parte de este bloque que cae dentro de {@code [desde, hasta)}; vacío si no la toca. */
    public Optional<BloqueOcupado> recortadoA(int desde, int hasta) {
        int inicio = Math.max(tramo.desde(), desde);
        int fin = Math.min(tramo.hasta(), hasta);
        if (inicio >= fin) {
            return Optional.empty();
        }
        return Optional.of(new BloqueOcupado(new TramoDelDia(inicio, fin), tipo, titulo));
    }
}
