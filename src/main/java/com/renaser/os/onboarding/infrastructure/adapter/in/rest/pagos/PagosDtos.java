package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PagosDeUnaPersona;
import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PaginaDeCuotas;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CuotaPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.PedidoDeCronograma;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.CuotaVista;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los cuerpos de la API de pagos (spec 032). Los nombres van en inglés, como el resto de la API que consume la app; los textos que se ven los escribe la app.
 */
final class PagosDtos {

    private PagosDtos() {
    }

    // ---------------------------------------------------------------------------------- lo que entra

    /** Una cuota que pide Administración: la fecha del calendario ({@code yyyy-MM-dd}) y el monto (hasta dos decimales). */
    record InstallmentRequest(LocalDate dueDate, BigDecimal amount) {

        CuotaPedida aCuota() {
            return new CuotaPedida(dueDate, amount);
        }
    }

    /** {@code mode}: {@code NONE} (pagó completo) o {@code SCHEDULE} (con cronograma: {@code currency} y {@code installments}). */
    record PaymentRequest(String mode, String currency, List<InstallmentRequest> installments) {

        PedidoDeCronograma aPedido() {
            if ("NONE".equals(mode)) {
                return PedidoDeCronograma.sinCronograma();
            }
            if (!"SCHEDULE".equals(mode)) {
                throw new IllegalArgumentException("mode es NONE (pagó completo) o SCHEDULE (con cronograma)");
            }
            return new PedidoDeCronograma(true, currency, installments == null ? List.of()
                    : installments.stream().map(i -> i == null ? null : i.aCuota()).toList());
        }
    }

    /** {@code status}: {@code PAID} o {@code PENDING}. */
    record MarkRequest(String status) {

        EstadoDeCuota aEstado() {
            return estadoDesde(status);
        }
    }

    /** {@code PAID} o {@code PENDING}: el mismo nombre del estado que devuelve {@link InstallmentResponse}. */
    static EstadoDeCuota estadoDesde(String status) {
        if ("PAID".equals(status)) {
            return EstadoDeCuota.PAGADA;
        }
        if ("PENDING".equals(status)) {
            return EstadoDeCuota.PENDIENTE;
        }
        throw new IllegalArgumentException("status es PAID o PENDING");
    }

    /** {@code reviewed}: la casilla «Revisé mis fechas y montos.»; {@code name}: «Tu nombre». */
    record ConfirmRequest(String name, Boolean reviewed) {
    }

    // ---------------------------------------------------------------------------------- lo que sale

    /** {@code changedByName} solo lo llena la lectura de Administración. */
    record InstallmentResponse(UUID id, LocalDate dueDate, BigDecimal amount, String status, Instant statusChangedAt, String statusChangedByName) {

        static InstallmentResponse from(CuotaVista vista) {
            var cuota = vista.cuota();
            return new InstallmentResponse(cuota.id(), cuota.venceEl(), cuota.monto(), cuota.pagada() ? "PAID" : "PENDING", cuota.estadoCambiadoEn(), vista.marcadaPor());
        }
    }

    /** {@code state}: {@code NOT_YET}, {@code CONFIRMED} o {@code OUTDATED} (confirmó otro cronograma y Administración cambió fechas o montos después). */
    record ConfirmationResponse(String state, Instant confirmedAt, String name) {
    }

    /** {@code mode}: {@code NONE} o {@code SCHEDULE}; {@code currency} y {@code confirmation} solo con cronograma. */
    record PaymentsResponse(String mode, String currency, List<InstallmentResponse> installments, ConfirmationResponse confirmation) {

        static PaymentsResponse from(PagoAnotado pago) {
            if (pago == null) {
                return null;
            }
            if (pago.modalidad() == ModalidadDePago.SIN_CRONOGRAMA) {
                return new PaymentsResponse("NONE", null, List.of(), null);
            }
            return new PaymentsResponse("SCHEDULE", pago.moneda().name(), pago.cuotas().stream().map(InstallmentResponse::from).toList(),
                    new ConfirmationResponse(switch (pago.confirmacion()) {
                        case SIN_CONFIRMAR -> "NOT_YET";
                        case CONFIRMADA -> "CONFIRMED";
                        case DESACTUALIZADA -> "OUTDATED";
                    }, pago.confirmadaEn(), pago.nombreConfirmado()));
        }
    }

    record StepResponse(String key, boolean done) {
    }

    /** Yo → «Mi ingreso». {@code payments} nulo = no hay nada que mostrar y la app no dibuja la fila. */
    record MiIngresoResponse(List<StepResponse> steps, PaymentsResponse payments) {

        static MiIngresoResponse from(MiIngreso ingreso) {
            return new MiIngresoResponse(ingreso.pasos().stream().map(paso -> new StepResponse(claveDelPaso(paso.clave()), paso.hecho())).toList(),
                    PaymentsResponse.from(ingreso.pagos()));
        }

        /** Las claves de la API van en inglés, como el resto de sus nombres. */
        private static String claveDelPaso(MiIngreso.ClaveDePaso clave) {
            return switch (clave) {
                case DIAGNOSTICO -> "DIAGNOSIS";
                case ACUERDO -> "AGREEMENT";
                case PAGOS -> "PAYMENTS";
            };
        }
    }

    /**
     * Una fila de la lectura de todos (D-338): el id de la persona (el {@code id} de {@code GET /admin/trainees}), su nombre, su correo y su pago con el mismo cuerpo que
     * la ficha. {@code payments.installments} trae solo las cuotas que cumplen el pedido.
     */
    record PersonInstallmentsResponse(UUID personId, String personName, String email, PaymentsResponse payments) {

        static PersonInstallmentsResponse from(PagosDeUnaPersona fila) {
            return new PersonInstallmentsResponse(fila.persona().value(), fila.nombre(), fila.correo(), PaymentsResponse.from(fila.pago()));
        }
    }

    /** La página de la lectura de todos, con la forma de {@code GET /admin/trainees}: {@code total} es de personas, en todas las páginas. */
    record InstallmentsPageResponse(List<PersonInstallmentsResponse> content, long total, int page, int size) {

        static InstallmentsPageResponse from(PaginaDeCuotas pagina) {
            return new InstallmentsPageResponse(pagina.contenido().stream().map(PersonInstallmentsResponse::from).toList(), pagina.total(), pagina.pagina(),
                    pagina.tamano());
        }
    }

    /** La ficha de Administración: el nombre de la persona y su pago ({@code payments} nulo = nada anotado). */
    record PersonPaymentsResponse(String personName, PaymentsResponse payments) {

        static PersonPaymentsResponse from(VistaDePagos vista) {
            return new PersonPaymentsResponse(vista.nombreDeLaPersona(), PaymentsResponse.from(vista.pago()));
        }
    }
}
