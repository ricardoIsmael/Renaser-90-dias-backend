package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Deja la constancia de que la persona aceptó los términos vigentes de su formación (spec 027, FR-011, FR-012) y de lo que se le mostró en «Mi aceptación»
 * (spec 039). Si además firmó su cronograma de pagos, dice qué confirmar en su estado de ingreso.
 */
public interface RegistrarAceptacionDeTerminosUseCase {

    /**
     * @throws IllegalStateException (409) si dejó de ser la vigente la versión que leyó, si ya no es el inicio que vio o si cambió el cronograma que firmó: la app
     *                               relee y avisa; nada queda registrado
     */
    ResultadoDeLaAceptacion registrar(UserId persona, PedidoDeAceptacion pedido);

    /**
     * @param versionId             la versión que la persona leyó; {@code null} (una app anterior) = la vigente de su formación
     * @param inicioVisto           el inicio de su programa que vio en «Mi aceptación»; {@code null} (una app anterior) = no se compara
     * @param huellaDelCronograma   la huella del cronograma que vio y aceptó; {@code null} = no firma ninguno (sin cronograma, quien ya estaba dentro, una app anterior)
     */
    record PedidoDeAceptacion(UUID versionId, LocalDate inicioVisto, String huellaDelCronograma) {

        /** Lo de antes de la spec 039: solo la versión. */
        public static PedidoDeAceptacion deLaVersion(UUID versionId) {
            return new PedidoDeAceptacion(versionId, null, null);
        }
    }

    /** @param cronograma lo que hay que confirmar en el estado de ingreso de la persona; vacío si no firmó cronograma (o ya lo tenía confirmado con esta huella) */
    record ResultadoDeLaAceptacion(Optional<ConfirmacionDelCronograma> cronograma) {

        public static ResultadoDeLaAceptacion sinCronograma() {
            return new ResultadoDeLaAceptacion(Optional.empty());
        }
    }

    /** La confirmación de la spec 032: el nombre que se guarda, tal cual, y la huella de lo confirmado. */
    record ConfirmacionDelCronograma(String nombre, String huella) {
    }
}
