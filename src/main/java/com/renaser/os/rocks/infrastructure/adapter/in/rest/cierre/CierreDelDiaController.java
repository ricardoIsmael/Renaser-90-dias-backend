package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * «Cierra tu día» (spec 017): {@code GET /api/v1/rocks/day-closure?date=} lee el cierre de un día de la propia persona y
 * {@code PUT /api/v1/rocks/day-closure} cierra el de hoy. Opcional, sin puntos: <b>cerrar nunca completa una acción</b>.
 *
 * <p>Datos de una persona, con notas que escribió ella: nada se guarda en caché ({@code no-store}) y nada se loguea. El permiso
 * es el de seguir el propio programa; que la cuenta no esté suspendida y que el programa esté andando lo decide el servicio.
 */
@RestController
@RequestMapping("/api/v1/rocks/day-closure")
@Funcionalidad("plan")
public class CierreDelDiaController {

    private final ConsultarCierreDelDiaUseCase consultar;
    private final CerrarElDiaUseCase cerrar;

    public CierreDelDiaController(ConsultarCierreDelDiaUseCase consultar, CerrarElDiaUseCase cerrar) {
        this.consultar = consultar;
        this.cerrar = cerrar;
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "el propio cierre; cuenta activa y programa andando (AccesoARocas)")
    @GetMapping
    public ResponseEntity<CierreDelDiaResponse> consultar(@ActorAutenticado UserId actor,
                                                          @RequestParam(name = "date", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return sinCache(CierreDelDiaResponse.from(consultar.consultar(actor, date)));
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "el propio cierre de HOY; cuenta activa y programa andando (AccesoARocas)")
    @PutMapping
    public ResponseEntity<CierreDelDiaResponse> cerrar(@ActorAutenticado UserId actor, @Valid @RequestBody CerrarElDiaRequest request) {
        var acciones = request.acciones().stream()
                .map(a -> new RespuestaDeAccion(a.id(), a.motivo(), a.manana(), a.nota()))
                .toList();
        return sinCache(CierreDelDiaResponse.from(cerrar.cerrar(new CerrarElDia(actor, request.fecha(), acciones, request.nota()))));
    }

    private static ResponseEntity<CierreDelDiaResponse> sinCache(CierreDelDiaResponse cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
