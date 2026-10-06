package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.animal;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarNombreDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.RestaurarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.VerAnimalesDeFaseUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El animal de cada fase (D-258). Leer lo ve cualquier cuenta activa (Yo); cambiarlo, solo ADMIN y
 * ALCHEMIST (el servicio rechaza a los demás roles y a las cuentas suspendidas). Todo lo que cambia
 * responde las cuatro fases enteras. Los cuerpos no llevan {@code @Valid}: valida el dominio, con
 * mensajes que la app muestra tal cual, y después de saber si quien pide puede (403 antes que 400).
 */
@RestController
@RequestMapping("/api/v1/phase-animals")
public class AnimalesDeFaseController {

    private static final String SOLO_ADMIN = "solo ADMIN/ALCHEMIST activos (D-258): el servicio rechaza a los "
            + "demás roles y a las cuentas suspendidas";

    private final VerAnimalesDeFaseUseCase verUseCase;
    private final CambiarImagenDeAnimalUseCase imagenUseCase;
    private final RestaurarImagenDeAnimalUseCase restaurarUseCase;
    private final CambiarNombreDeAnimalUseCase nombreUseCase;

    public AnimalesDeFaseController(VerAnimalesDeFaseUseCase verUseCase, CambiarImagenDeAnimalUseCase imagenUseCase,
                                    RestaurarImagenDeAnimalUseCase restaurarUseCase,
                                    CambiarNombreDeAnimalUseCase nombreUseCase) {
        this.verUseCase = verUseCase;
        this.imagenUseCase = imagenUseCase;
        this.restaurarUseCase = restaurarUseCase;
        this.nombreUseCase = nombreUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public List<AnimalDeFaseResponse> ver(@ActorAutenticado UserId actorId) {
        return responder(verUseCase.ver(actorId));
    }

    @RequiresPermission(value = Permission.MANAGE_PHASE_ANIMALS, scope = SOLO_ADMIN)
    @PostMapping("/{fase}/image/upload-url")
    public UrlDeSubidaDeImagenResponse solicitarSubida(@ActorAutenticado UserId actorId, @PathVariable int fase,
                                                       @RequestBody SolicitarSubidaDeImagenRequest request) {
        return UrlDeSubidaDeImagenResponse.from(imagenUseCase.solicitarSubida(actorId, fase, request.contentType()));
    }

    @RequiresPermission(value = Permission.MANAGE_PHASE_ANIMALS, scope = SOLO_ADMIN)
    @PostMapping("/{fase}/image/confirm")
    public List<AnimalDeFaseResponse> confirmar(@ActorAutenticado UserId actorId, @PathVariable int fase,
                                                @RequestBody ConfirmarImagenRequest request) {
        return responder(imagenUseCase.confirmar(actorId, fase, request.ruta()));
    }

    @RequiresPermission(value = Permission.MANAGE_PHASE_ANIMALS, scope = SOLO_ADMIN)
    @DeleteMapping("/{fase}/image")
    public List<AnimalDeFaseResponse> restaurarImagen(@ActorAutenticado UserId actorId, @PathVariable int fase) {
        return responder(restaurarUseCase.restaurar(actorId, fase));
    }

    @RequiresPermission(value = Permission.MANAGE_PHASE_ANIMALS, scope = SOLO_ADMIN)
    @PutMapping("/{fase}/name")
    public List<AnimalDeFaseResponse> cambiarNombre(@ActorAutenticado UserId actorId, @PathVariable int fase,
                                                    @RequestBody CambiarNombreRequest request) {
        return responder(nombreUseCase.cambiar(actorId, fase, request.nombre()));
    }

    private static List<AnimalDeFaseResponse> responder(List<AnimalDeFaseVista> vistas) {
        return vistas.stream().map(AnimalDeFaseResponse::from).toList();
    }
}
