package com.renaser.os.habits.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasDeMiFormacionUseCase.CategoriasDeLaFormacion;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;

/** {@code formation} es el codigo de la formacion de las categorias (nulo si la persona no pertenece a ninguna). */
public record CategoriasDeLaFormacionResponse(String formation, List<CategoriaDeHabitoResponse> categories) {

    public static CategoriasDeLaFormacionResponse from(CategoriasDeLaFormacion categorias) {
        return new CategoriasDeLaFormacionResponse(categorias.formacion().map(Formacion::codigo).orElse(null),
                categorias.categorias().stream().map(CategoriaDeHabitoResponse::from).toList());
    }
}
