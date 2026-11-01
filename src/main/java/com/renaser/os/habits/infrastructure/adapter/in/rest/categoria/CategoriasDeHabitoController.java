package com.renaser.os.habits.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasDeMiFormacionUseCase;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las categorias de habito (las dimensiones de Training) de la formacion de quien pregunta (spec 023). Es un dato de lectura que Training
 * pide una vez; la lista de habitos de la persona ya viene filtrada por su formacion en {@code GET /habits}.
 */
@RestController
@RequestMapping("/api/v1/habit-categories")
@Funcionalidad("habitos")
public class CategoriasDeHabitoController {

    private final ConsultarCategoriasDeMiFormacionUseCase consultarUseCase;

    public CategoriasDeHabitoController(ConsultarCategoriasDeMiFormacionUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    /** {@code formation} solo lo honra para quien ve las tres formaciones; para cualquier otro, otra formacion responde 404. */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public CategoriasDeLaFormacionResponse listar(@ActorAutenticado UserId actor,
                                                  @RequestParam(name = "formation", required = false) String formation) {
        Formacion pedida = formation == null || formation.isBlank() ? null : Formacion.desde(formation);
        return CategoriasDeLaFormacionResponse.from(consultarUseCase.consultar(actor, pedida));
    }
}
