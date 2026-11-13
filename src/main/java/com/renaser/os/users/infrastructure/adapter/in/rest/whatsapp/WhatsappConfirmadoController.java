package com.renaser.os.users.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.DireccionIpDelCliente;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.ConfirmarCommand;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.PedirCodigoCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yo → «WhatsApp» (spec 051). Siempre sobre la cuenta de la sesion: no hay id en la ruta ni en el cuerpo. El numero se guarda solo al
 * confirmar el codigo que llega a ese numero; nunca viaja completo en una respuesta, solo enmascarado ({@code +51 9•• ••• •21}).
 */
@RestController
@RequestMapping("/api/v1/users/me/whatsapp")
@Funcionalidad("cuenta.whatsapp")
public class WhatsappConfirmadoController {

    private final WhatsappConfirmadoUseCase useCase;

    public WhatsappConfirmadoController(WhatsappConfirmadoUseCase useCase) {
        this.useCase = useCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @GetMapping
    public EstadoResponse estado(@ActorAutenticado UserId actor) {
        var estado = useCase.estado(actor);
        return new EstadoResponse(estado.disponible(), estado.confirmado().orElse(null));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/code")
    public ResponseEntity<EnviadoResponse> pedirCodigo(@ActorAutenticado UserId actor, @RequestBody @Valid PedirCodigoRequest request,
                                                       HttpServletRequest servletRequest) {
        String enmascarado = useCase.pedirCodigo(new PedirCodigoCommand(actor, request.whatsapp(), DireccionIpDelCliente.de(servletRequest)));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new EnviadoResponse(enmascarado));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/confirm")
    public ConfirmadoResponse confirmar(@ActorAutenticado UserId actor, @RequestBody @Valid ConfirmarRequest request,
                                        HttpServletRequest servletRequest) {
        String enmascarado = useCase.confirmar(new ConfirmarCommand(actor, request.whatsapp(), request.code(),
                DireccionIpDelCliente.de(servletRequest)));
        return new ConfirmadoResponse(enmascarado);
    }

    /** @param available hay con que mandar codigos @param confirmed el numero confirmado, enmascarado; {@code null} si no hay */
    public record EstadoResponse(boolean available, String confirmed) {
    }

    /** @param sentTo el numero enmascarado al que se mando; nunca el completo */
    public record EnviadoResponse(String sentTo) {
    }

    /** @param confirmed el numero confirmado, enmascarado */
    public record ConfirmadoResponse(String confirmed) {
    }

    public record PedirCodigoRequest(@NotBlank @Size(max = 40) String whatsapp) {
        @Override
        public String toString() {
            return "PedirCodigoRequest[whatsapp=oculto]";
        }
    }

    public record ConfirmarRequest(@NotBlank @Size(max = 40) String whatsapp, @NotBlank @Size(max = 12) String code) {
        @Override
        public String toString() {
            return "ConfirmarRequest[whatsapp=oculto, code=oculto]";
        }
    }
}
