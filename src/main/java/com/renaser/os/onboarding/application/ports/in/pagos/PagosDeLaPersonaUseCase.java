package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los pagos de una persona vistos por Administración (spec 032): solo ADMIN y ALCHEMIST con la cuenta activa (403 para cualquier otro rol, Soporte TI incluido). Se usa desde la
 * ficha y desde la hoja «Aceptar». Cada operación devuelve lo que quedó.
 *
 * <p>Errores: 404 si la persona o la cuota no existen; 400 si lo pedido no cumple las reglas (moneda, monto, fecha, cantidad); 409 si choca con lo ya pagado o con el estado
 * (cuota pagada, ya hay cuotas pagadas, última cuota, sin cronograma).
 */
public interface PagosDeLaPersonaUseCase {

    /** El pago de la persona; sin nada anotado, {@code pago} nulo. */
    VistaDePagos ver(UserId actor, UserId persona);

    /**
     * Le anota «sin cronograma» o le arma el cronograma entero, reemplazando el que tuviera. No se puede si ya tiene cuotas pagadas (409): lo pagado no se toca.
     */
    VistaDePagos asignar(UserId actor, UserId persona, PedidoDeCronograma pedido);

    VistaDePagos agregarCuota(UserId actor, UserId persona, CuotaPedida cuota);

    VistaDePagos editarCuota(UserId actor, UserId persona, CambioDeCuota cambio);

    VistaDePagos quitarCuota(UserId actor, UserId persona, UUID cuota);

    /** Marca la cuota pagada o la devuelve a pendiente; queda quién y cuándo. */
    VistaDePagos marcarCuota(UserId actor, UserId persona, MarcaDeCuota marca);

    /** @param moneda {@code PEN} o {@code USD}; solo se lee con cronograma */
    record PedidoDeCronograma(boolean conCronograma, String moneda, List<CuotaPedida> cuotas) {

        /** El de «Sin cronograma (pagó completo)». */
        public static PedidoDeCronograma sinCronograma() {
            return new PedidoDeCronograma(false, null, List.of());
        }
    }

    record CuotaPedida(LocalDate venceEl, BigDecimal monto) {
    }

    record CambioDeCuota(UUID cuota, CuotaPedida nueva) {
    }

    record MarcaDeCuota(UUID cuota, EstadoDeCuota estado) {
    }
}
