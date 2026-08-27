package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase.MotivoNoEntregable;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase.ResultadoVerificacionDominio;

/**
 * Respuesta de {@code POST /account-requests/verify-email}. Los nombres son en ingles y en
 * {@code snake_case} porque replican EL CONTRATO QUE LA APP YA CONSUME (CLAUDE.MD §8: el cliente
 * movil no debe enterarse de que hay otro backend detras) — no es el estilo del resto del
 * proyecto, y esa es justamente la razon de que la traduccion viva aca, en el borde, y no en el
 * caso de uso.
 *
 * <p>{@code deliverable} es {@code Boolean} y no {@code boolean}: {@code null} significa "no se
 * pudo averiguar", que es un tercer estado real del contrato. {@code reason} solo viene cuando
 * {@code deliverable} es {@code false}.
 */
public record VerificacionDominioResponse(Boolean deliverable, String reason) {

    public static VerificacionDominioResponse from(ResultadoVerificacionDominio resultado) {
        return new VerificacionDominioResponse(resultado.entregable(), aWire(resultado.motivo()));
    }

    /**
     * Mapeo explicito enum -> string del contrato. A mano y no con {@code name().toLowerCase()}:
     * asi renombrar una constante del enum rompe la compilacion en vez de romper la app en
     * silencio (CLAUDE.MD §5.4.5, "a mano hacia afuera del sistema").
     */
    private static String aWire(MotivoNoEntregable motivo) {
        if (motivo == null) {
            return null;
        }
        return switch (motivo) {
            case SIN_MX -> "sin_mx";
            case DOMINIO_INEXISTENTE -> "dominio_inexistente";
            case FORMATO -> "formato";
        };
    }
}
