package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;

import java.time.Instant;
import java.util.List;

/**
 * Lo que se lee del pago de una persona (spec 032): la ve ella (en «Mi ingreso») y la ve Administración (en su ficha).
 *
 * @param nombreDeLaPersona para el título de Administración; la persona misma no lo necesita
 * @param pago              {@code null} si no tiene nada anotado
 */
public record VistaDePagos(String nombreDeLaPersona, PagoAnotado pago) {

    /**
     * @param moneda          {@code null} sin cronograma
     * @param cuotas          vacío sin cronograma
     * @param confirmacion    {@code null} sin cronograma (no hay nada que confirmar)
     * @param confirmadaEn    cuándo confirmó, también cuando ya está desactualizada (lo que cambió es lo acordado, no el hecho de haberlo confirmado)
     * @param nombreConfirmado lo que escribió al confirmar
     */
    public record PagoAnotado(ModalidadDePago modalidad, Moneda moneda, List<CuotaVista> cuotas, EstadoDeConfirmacion confirmacion, Instant confirmadaEn,
                              String nombreConfirmado) {
    }

    /** @param marcadaPor el nombre de quien la marcó por última vez; solo lo lee Administración (la persona no lo ve) */
    public record CuotaVista(Cuota cuota, String marcadaPor) {
    }
}
