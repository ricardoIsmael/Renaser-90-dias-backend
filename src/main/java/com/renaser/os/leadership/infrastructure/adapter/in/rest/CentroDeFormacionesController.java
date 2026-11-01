package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.DetalleDeFormacion;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.Resumen;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.TarjetaDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El centro «Formaciones» de Administración (spec 022, FR-025): las tres formaciones con su información principal y el detalle de cada una. Editar
 * una formación está en {@code FormacionesAdminController} (users), que es el dueño de la tabla.
 */
@RestController
@RequestMapping("/api/v1/admin/formaciones")
@Funcionalidad("admin.personas")
public class CentroDeFormacionesController {

    private final ConsultarCentroDeFormacionesUseCase centro;

    public CentroDeFormacionesController(ConsultarCentroDeFormacionesUseCase centro) {
        this.centro = centro;
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es el servicio: ADMIN/ALCHEMIST activos")
    @GetMapping
    public List<TarjetaResponse> tarjetas(@ActorAutenticado UserId actor) {
        return centro.tarjetas(actor).stream().map(TarjetaResponse::from).toList();
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es el servicio: ADMIN/ALCHEMIST activos")
    @GetMapping("/{codigo}")
    public DetalleResponse detalle(@PathVariable String codigo, @ActorAutenticado UserId actor) {
        return DetalleResponse.from(centro.detalle(actor, Formacion.deLaRuta(codigo)));
    }

    public record ResumenResponse(String codigo, String nombre, String descripcion, boolean activa, List<Integer> duracionesDias,
                                  List<String> etiquetasDeDuracion, int version) {

        static ResumenResponse from(Resumen r) {
            return new ResumenResponse(r.formacion().codigo(), r.nombre(), r.descripcion(), r.activa(), r.duracionesDias(),
                    r.etiquetasDeDuracion(), r.version());
        }
    }

    public record TarjetaResponse(ResumenResponse resumen, int aprendicesActivos, int personal, int grupos, int porAtender) {

        static TarjetaResponse from(TarjetaDeFormacion t) {
            return new TarjetaResponse(ResumenResponse.from(t.resumen()), t.aprendicesActivos(), t.personal(), t.grupos(), t.porAtender());
        }
    }

    public record PersonasResponse(int aprendices, int porEmpezar, int enCurso, int terminaron, int personal) {
    }

    public record GruposResponse(int total, int conMentor, int sinMentor, int recepcion) {
    }

    public record PorAtenderResponse(int gruposSinMentor, int aprendicesSinGrupo, int personalSinFormacion) {
    }

    public record DetalleResponse(ResumenResponse resumen, PersonasResponse personas, GruposResponse grupos, PorAtenderResponse porAtender) {

        static DetalleResponse from(DetalleDeFormacion d) {
            var p = d.personas();
            var g = d.grupos();
            var a = d.porAtender();
            return new DetalleResponse(ResumenResponse.from(d.resumen()),
                    new PersonasResponse(p.aprendices(), p.porEmpezar(), p.enCurso(), p.terminaron(), p.personal()),
                    new GruposResponse(g.total(), g.conMentor(), g.sinMentor(), g.recepcion()),
                    new PorAtenderResponse(a.gruposSinMentor(), a.aprendicesSinGrupo(), a.personalSinFormacion()));
        }
    }
}
