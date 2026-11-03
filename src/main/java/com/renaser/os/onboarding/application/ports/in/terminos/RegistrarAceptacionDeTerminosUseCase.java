package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Deja la constancia de que la persona aceptó los términos vigentes de su formación (spec 027, FR-011, FR-012) y de lo que se le mostró en «Mi aceptación»
 * (spec 039). El cronograma de pagos NO interviene: no se muestra ni se firma con los términos (D-315, decisión del dueño del 2026-11-02: los pagos son asíncronos,
 * los llena Administración después y la persona los ve y los confirma en «Mi ingreso», spec 032).
 */
public interface RegistrarAceptacionDeTerminosUseCase {

    /**
     * @throws IllegalStateException (409) si dejó de ser la vigente la versión que leyó o si ya no es el inicio que vio: la app relee y avisa; nada queda registrado
     */
    void registrar(UserId persona, PedidoDeAceptacion pedido);

    /**
     * @param versionId   la versión que la persona leyó; {@code null} (una app anterior) = la vigente de su formación
     * @param inicioVisto el inicio de su programa que vio en «Mi aceptación»; {@code null} (una app anterior) = no se compara
     */
    record PedidoDeAceptacion(UUID versionId, LocalDate inicioVisto) {

        /** Lo de antes de la spec 039: solo la versión. */
        public static PedidoDeAceptacion deLaVersion(UUID versionId) {
            return new PedidoDeAceptacion(versionId, null);
        }
    }
}
