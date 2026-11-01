package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.PlanDePagoPedido;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.accountrequest.PedidoDeIngreso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo OPCIONAL de {@code POST /api/v1/account-requests/{id}/approve} (D-265). {@code role} es el rol con el que
 * entra (los mismos códigos que {@code PATCH /users/{id}/role}); sin cuerpo o sin {@code role}, entra como TRAINEE,
 * que es lo que mandan la app 1.5.0 y el panel de solicitudes.
 *
 * <p>{@code rolPersonalizadoId} (spec 002, fase 2; D-272), opcional y aditivo: entra con ese rol personalizado; el rol
 * base sale de él y {@code role} se ignora.
 *
 * <p>Spec 025 (D-302), todo opcional y aditivo: {@code formations} (códigos: una para el aprendiz, una o varias para el
 * personal, ninguna para Administración y Alquimista), {@code durationDays} (solo el aprendiz de una formación con más de
 * una duración) y {@code withDiagnosis} (la ficha con diagnóstico en lugar de la estándar). Sin ellos entra como siempre:
 * Escuela, su duración única y sin diagnóstico.
 *
 * <p>Spec 032 (D-307), opcional y aditivo: {@code payment} es el pago de la persona: {@code mode} {@code NONE} («Sin cronograma (pagó completo)») o {@code SCHEDULE} («Con cronograma», con su
 * {@code currency} {@code PEN} o {@code USD} y sus {@code installments}). Sin {@code payment} (lo que manda una app anterior) no se anota nada.
 */
public record ApproveAccountRequestRequest(UserRole role, UUID rolPersonalizadoId, List<String> formations,
                                           Integer durationDays, Boolean withDiagnosis, Payment payment) {

    /** @param mode {@code NONE} o {@code SCHEDULE}; cualquier otro valor es 400 */
    public record Payment(String mode, String currency, List<Installment> installments) {

        PlanDePagoPedido aPlan() {
            if ("NONE".equals(mode)) {
                return PlanDePagoPedido.sinCronograma();
            }
            if (!"SCHEDULE".equals(mode)) {
                throw new IllegalArgumentException("payment.mode es NONE (pagó completo) o SCHEDULE (con cronograma)");
            }
            return new PlanDePagoPedido(true, currency, installments == null ? List.of()
                    : installments.stream().map(cuota -> cuota == null ? null : new PlanDePagoPedido.Cuota(cuota.dueDate(), cuota.amount())).toList());
        }
    }

    public record Installment(LocalDate dueDate, BigDecimal amount) {
    }

    /** El pedido de formación, con los códigos ya traducidos ({@code 400} si alguno no es de una formación) y el pago, si lo trae. */
    public PedidoDeIngreso pedidoDeIngreso() {
        List<Formacion> codigos = formations == null ? List.of() : formations.stream().map(Formacion::desde).toList();
        return new PedidoDeIngreso(java.util.Set.copyOf(codigos), durationDays, Boolean.TRUE.equals(withDiagnosis), payment == null ? null : payment.aPlan());
    }
}
