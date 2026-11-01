package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CambioDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.MarcaDeCuota;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.InstallmentRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.MarkRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.PaymentRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.PersonPaymentsResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * La sección «Pagos» de la ficha de una persona en Administración (spec 032): ver su cronograma, asignarlo, ajustar las cuotas pendientes y marcar cada cuota pagada o pendiente.
 * Solo ADMIN y ALCHEMIST con la cuenta activa; el servicio rechaza a los demás roles, Soporte TI incluido.
 */
@RestController
@RequestMapping("/api/v1/admin/pagos/personas/{personaId}")
@Funcionalidad("pagos")
public class PagosAdminController {

    static final String SOLO_ADMIN = "solo ADMIN y ALCHEMIST activos (spec 032): el servicio rechaza a los demás roles, Soporte TI incluido, y a las cuentas suspendidas";

    private final PagosDeLaPersonaUseCase pagos;

    public PagosAdminController(PagosDeLaPersonaUseCase pagos) {
        this.pagos = pagos;
    }

    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @GetMapping
    public PersonPaymentsResponse ver(@ActorAutenticado UserId actor, @PathVariable UUID personaId) {
        return PersonPaymentsResponse.from(pagos.ver(actor, UserId.of(personaId)));
    }

    /** «Sin cronograma» o el cronograma entero, reemplazando el anterior. 409 si ya hay cuotas pagadas. */
    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @PutMapping
    public PersonPaymentsResponse asignar(@ActorAutenticado UserId actor, @PathVariable UUID personaId, @RequestBody PaymentRequest request) {
        return PersonPaymentsResponse.from(pagos.asignar(actor, UserId.of(personaId), request.aPedido()));
    }

    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @PostMapping("/cuotas")
    public PersonPaymentsResponse agregar(@ActorAutenticado UserId actor, @PathVariable UUID personaId, @RequestBody InstallmentRequest request) {
        return PersonPaymentsResponse.from(pagos.agregarCuota(actor, UserId.of(personaId), request.aCuota()));
    }

    /** Solo una cuota pendiente: una pagada es 409. */
    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @PutMapping("/cuotas/{cuotaId}")
    public PersonPaymentsResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID personaId, @PathVariable UUID cuotaId,
                                         @RequestBody InstallmentRequest request) {
        return PersonPaymentsResponse.from(pagos.editarCuota(actor, UserId.of(personaId), new CambioDeCuota(cuotaId, request.aCuota())));
    }

    /** Solo una cuota pendiente que no sea la última. */
    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @DeleteMapping("/cuotas/{cuotaId}")
    public PersonPaymentsResponse quitar(@ActorAutenticado UserId actor, @PathVariable UUID personaId, @PathVariable UUID cuotaId) {
        return PersonPaymentsResponse.from(pagos.quitarCuota(actor, UserId.of(personaId), cuotaId));
    }

    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = SOLO_ADMIN)
    @PutMapping("/cuotas/{cuotaId}/estado")
    public PersonPaymentsResponse marcar(@ActorAutenticado UserId actor, @PathVariable UUID personaId, @PathVariable UUID cuotaId,
                                         @RequestBody MarkRequest request) {
        return PersonPaymentsResponse.from(pagos.marcarCuota(actor, UserId.of(personaId), new MarcaDeCuota(cuotaId, request.aEstado())));
    }
}
