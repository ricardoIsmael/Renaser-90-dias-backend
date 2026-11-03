package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.CategoriasDeLaFormacionFinder;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarCategoriasDeHabitoPort;
import com.renaser.os.rag.domain.model.habitopersonal.CategoriaDelHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementa {@link ConsultarCategoriasDeHabitoPort} delegando en el contrato publico de {@code habits} (D-41). Solo traduce el tipo.
 */
@Component
class ConsultarCategoriasDeHabitoAdapter implements ConsultarCategoriasDeHabitoPort {

    private final CategoriasDeLaFormacionFinder categorias;

    ConsultarCategoriasDeHabitoAdapter(CategoriasDeLaFormacionFinder categorias) {
        this.categorias = categorias;
    }

    @Override
    public List<CategoriaDelHabito> deLaFormacionDe(UserId participanteId) {
        return categorias.activasDe(participanteId).stream()
                .map(categoria -> new CategoriaDelHabito(categoria.clave(), categoria.nombre()))
                .toList();
    }
}
