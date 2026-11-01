package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ConsultarComunidadDeLaFormacionUseCase;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los tipos de comunidad de una formación con cuántas conversaciones tiene cada uno (spec 024, FR-033, FR-035). Prender o apagar un tipo es
 * {@code PUT /api/v1/admin/formaciones/{codigo}/comunidad/{tipo}}, que atiende {@code users} (el dato es del catálogo de formaciones).
 */
@RestController
@RequestMapping("/api/v1/admin/formaciones/{codigo}/comunidad")
@Funcionalidad("chat")
public class ComunidadDeLaFormacionController {

    private final ConsultarComunidadDeLaFormacionUseCase consultar;

    public ComunidadDeLaFormacionController(ConsultarComunidadDeLaFormacionUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es el caso de uso: ADMIN/ALCHEMIST activos")
    @GetMapping
    public ComunidadResponse ver(@PathVariable String codigo, @ActorAutenticado UserId actorId) {
        Formacion formacion = Formacion.deLaRuta(codigo);
        return new ComunidadResponse(formacion.codigo(), consultar.consultar(actorId, formacion).stream()
                .map(t -> new TipoResponse(t.tipo().codigo(), t.prendido(), t.conversaciones())).toList());
    }

    /** {@code tipos} en el orden de siempre: general, grupo, soporte y directos. */
    public record ComunidadResponse(String formation, List<TipoResponse> tipos) {
    }

    /** {@code conversaciones}: cuántas esconde el tipo si se apaga. */
    public record TipoResponse(String tipo, boolean prendido, long conversaciones) {
    }
}
