package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase.FijarTipoCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prender o apagar un tipo de comunidad de una formación (spec 024, FR-034). La lectura con el conteo de conversaciones que cada tipo esconde
 * ({@code GET /admin/formaciones/{codigo}/comunidad}) la sirve {@code chat}, que es quien conoce las conversaciones. El guard real es
 * {@code RequireAdminGuard}: ADMIN y ALCHEMIST activos, nadie más.
 */
@RestController
@RequestMapping("/api/v1/admin/formaciones")
@Funcionalidad("admin.personas")
public class ComunidadDeLaFormacionAdminController {

    private final ConfigurarComunidadDeLaFormacionUseCase configurar;

    public ComunidadDeLaFormacionAdminController(ConfigurarComunidadDeLaFormacionUseCase configurar) {
        this.configurar = configurar;
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @PutMapping("/{codigo}/comunidad/{tipo}")
    public TiposPrendidosResponse fijar(@PathVariable String codigo, @PathVariable String tipo, @ActorAutenticado UserId actor,
                                        @RequestBody @Valid FijarRequest request) {
        var prendidos = configurar.fijar(new FijarTipoCommand(actor, Formacion.desde(codigo), TipoDeComunidad.desde(tipo),
                request.prendido()));
        return new TiposPrendidosResponse(prendidos.stream().map(TipoDeComunidad::codigo).sorted().toList());
    }

    public record FijarRequest(@NotNull Boolean prendido) {
    }

    /** Los tipos prendidos de la formación después del cambio. */
    public record TiposPrendidosResponse(List<String> prendidos) {
    }
}
