package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.web.DireccionIpDelCliente;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.CodigoDeRecuperacionCommand;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.CodigoDelRegistroCommand;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.NumeroDelRegistroCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
 * El codigo de verificacion por WhatsApp (spec 043, D-325), para cuando no llega por correo. Publico, sin sesion: lo pide quien todavia no tiene
 * cuenta (el alta) o no puede entrar (la recuperacion). El codigo se verifica por los endpoints de siempre.
 *
 * <p>Con el interruptor {@code auth.codigo_whatsapp} apagado (nace apagado, hasta que Meta apruebe la plantilla) los tres responden 404
 * {@code FUNCIONALIDAD_NO_DISPONIBLE}: la app, igual que con un servidor anterior, no ofrece nada.
 */
@RestController
@RequestMapping("/api/v1/auth/whatsapp-code")
@Funcionalidad("auth.codigo_whatsapp")
public class CodigoPorWhatsAppController {

    private final PedirCodigoPorWhatsAppUseCase useCase;

    public CodigoPorWhatsAppController(PedirCodigoPorWhatsAppUseCase useCase) {
        this.useCase = useCase;
    }

    @PublicEndpoint("Se consulta antes de tener cuenta o de poder entrar. Dice solo si el canal esta listo: no depende de ninguna cuenta.")
    @GetMapping
    public DisponibilidadResponse disponibilidad() {
        return new DisponibilidadResponse(useCase.disponible());
    }

    @PublicEndpoint("El alta: el correo todavia no es de nadie. Protegido por limites por origen, correo, numero y uno global.")
    @PostMapping("/registration")
    public ResponseEntity<EnviadoResponse> paraElRegistro(@RequestBody @Valid RegistroRequest request, HttpServletRequest servletRequest) {
        String enmascarado = useCase.paraElRegistro(new CodigoDelRegistroCommand(request.email(), request.whatsapp(),
                DireccionIpDelCliente.de(servletRequest)));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new EnviadoResponse(enmascarado));
    }

    @PublicEndpoint("El paso del WhatsApp del alta: dice solo si el numero ya es de otra cuenta (409 NUMERO_EN_USO). Decision del dueño (spec 051); protegido por limites por origen y global.")
    @PostMapping("/number")
    public ResponseEntity<Void> comprobarNumero(@RequestBody @Valid NumeroRequest request, HttpServletRequest servletRequest) {
        useCase.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand(request.whatsapp(), DireccionIpDelCliente.de(servletRequest)));
        return ResponseEntity.noContent().build();
    }

    @PublicEndpoint("Se pide cuando no se puede iniciar sesion. Responde 202 siempre, sin cuerpo: no revela si el correo tiene cuenta ni WhatsApp.")
    @PostMapping("/password")
    public ResponseEntity<Void> paraRecuperar(@RequestBody @Valid RecuperacionRequest request, HttpServletRequest servletRequest) {
        useCase.paraRecuperar(new CodigoDeRecuperacionCommand(request.email(), DireccionIpDelCliente.de(servletRequest)));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    public record DisponibilidadResponse(boolean available) {
    }

    /** @param sentTo el numero enmascarado ({@code +51 9•• ••• •21}); nunca el completo */
    public record EnviadoResponse(String sentTo) {
    }

    public record RegistroRequest(@NotBlank @Email String email, @NotBlank @Size(max = 40) String whatsapp) {
        @Override
        public String toString() {
            return "RegistroRequest[email=" + email + ", whatsapp=oculto]";
        }
    }

    public record NumeroRequest(@NotBlank @Size(max = 40) String whatsapp) {
        @Override
        public String toString() {
            return "NumeroRequest[whatsapp=oculto]";
        }
    }

    public record RecuperacionRequest(@NotBlank @Email String email) {
    }
}
