package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PedidoDeCuotas;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.InstallmentsPageResponse;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Las cuotas de todas las personas con cronograma en una sola lectura (D-338), para RenaserOs: hasta hoy tenía que pedir {@code GET /admin/pagos/personas/{id}} una
 * vez por persona. Mismo acceso que esa lectura: {@code MANAGE_PAYMENTS} y la guardia del servicio (solo ADMIN y ALCHEMIST activos).
 *
 * <p>Paginado como {@code GET /admin/trainees} ({@code page} desde 0, {@code size} de 1 a 200) y con su mismo filtro {@code formation} (vacío = «Todas»).
 */
@RestController
@RequestMapping("/api/v1/admin/pagos/cuotas")
@Funcionalidad("pagos")
public class CuotasDeTodosAdminController {

    private final CuotasDeTodosUseCase cuotas;

    public CuotasDeTodosAdminController(CuotasDeTodosUseCase cuotas) {
        this.cuotas = cuotas;
    }

    /**
     * @param desde  {@code yyyy-MM-dd}, inclusive: solo las cuotas que vencen ese día o después
     * @param hasta  {@code yyyy-MM-dd}, inclusive
     * @param status {@code PAID} o {@code PENDING}
     */
    @RequiresPermission(value = Permission.MANAGE_PAYMENTS, scope = PagosAdminController.SOLO_ADMIN)
    @GetMapping
    public InstallmentsPageResponse listar(@ActorAutenticado UserId actor,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size,
                                           @RequestParam(required = false) String formation,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           @RequestParam(required = false) String status) {
        Formacion formacion = formation == null || formation.isBlank() ? null : Formacion.desde(formation);
        var pedido = new PedidoDeCuotas(actor, page, size, formacion, desde, hasta, status == null || status.isBlank() ? null : PagosDtos.estadoDesde(status));
        return InstallmentsPageResponse.from(cuotas.listar(pedido));
    }
}
