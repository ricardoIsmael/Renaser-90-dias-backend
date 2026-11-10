package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Las cuotas de TODAS las personas con cronograma, de a una página (D-338). Lo pide RenaserOs, el CRM del dueño, que hasta hoy tenía que leer los pagos persona
 * por persona con {@link PagosDeLaPersonaUseCase#ver}. Mismas reglas de acceso que esa lectura: solo ADMIN y ALCHEMIST con la cuenta activa (403 para cualquier
 * otro rol, Soporte TI incluido).
 *
 * <p>Cada fila trae lo mismo que la ficha de una persona (su pago con las cuotas, la moneda y la confirmación), más su correo para que el CRM la cruce con
 * {@code GET /admin/trainees}.
 */
public interface CuotasDeTodosUseCase {

    int TAMANO_MAXIMO = 200;

    PaginaDeCuotas listar(PedidoDeCuotas pedido);

    /**
     * @param formacion {@code null} = «Todas»; si viene, solo las personas de esa formación (los aprendices que la cursan y el personal que la tiene asignada)
     * @param desde     {@code null} = sin límite; si viene, solo las cuotas que vencen ese día o después (fecha del calendario, sin zona: la de la cuota)
     * @param hasta     {@code null} = sin límite; si viene, solo las que vencen ese día o antes
     * @param estado    {@code null} = las dos; si viene, solo las cuotas en ese estado
     */
    record PedidoDeCuotas(UserId actor, int pagina, int tamano, Formacion formacion, LocalDate desde, LocalDate hasta, EstadoDeCuota estado) {

        public PedidoDeCuotas {
            Objects.requireNonNull(actor, "actor es obligatorio");
            if (pagina < 0) {
                throw new IllegalArgumentException("page no puede ser negativo");
            }
            if (tamano <= 0 || tamano > TAMANO_MAXIMO) {
                throw new IllegalArgumentException("size debe estar entre 1 y " + TAMANO_MAXIMO);
            }
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                throw new IllegalArgumentException("desde no puede ser posterior a hasta");
            }
        }
    }

    /** @param total cuántas personas cumplen el pedido, en todas las páginas */
    record PaginaDeCuotas(List<PagosDeUnaPersona> contenido, long total, int pagina, int tamano) {
    }

    /**
     * @param nombre su nombre completo; {@code null} si la cuenta ya no existe
     * @param correo el de su cuenta, el mismo de {@code GET /admin/trainees}; {@code null} si la cuenta ya no existe
     * @param pago   su cronograma, con solo las cuotas que cumplen el pedido; la confirmación se calcula siempre sobre el cronograma entero
     */
    record PagosDeUnaPersona(UserId persona, String nombre, String correo, PagoAnotado pago) {
    }
}
