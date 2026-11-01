package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.GruposPorFormacionFinder;
import com.renaser.os.community.api.GruposPorFormacionFinder.ResumenDeGrupos;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CifrasDeFormacionFinder;
import com.renaser.os.users.api.CifrasDeFormacionFinder.CifrasDePersonas;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Arma el centro «Formaciones» juntando lo que saben {@code users} (catálogo y personas) y {@code community} (grupos); por eso vive en
 * {@code leadership}, que ya depende de los dos. No calcula nada propio: cada cifra es la de su dueño.
 */
@Service
class CentroDeFormacionesService implements ConsultarCentroDeFormacionesUseCase {

    private final UserSummaryFinder usuarios;
    private final FormacionesFinder catalogo;
    private final CifrasDeFormacionFinder personas;
    private final GruposPorFormacionFinder grupos;

    CentroDeFormacionesService(UserSummaryFinder usuarios, FormacionesFinder catalogo, CifrasDeFormacionFinder personas,
                               GruposPorFormacionFinder grupos) {
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.personas = personas;
        this.grupos = grupos;
    }

    @Override
    public List<TarjetaDeFormacion> tarjetas(UserId actor) {
        exigirAdministracion(actor);
        int sinFormacion = personas.personalSinFormacion();
        return catalogo.todas().stream().map(f -> tarjeta(f, sinFormacion)).toList();
    }

    @Override
    public DetalleDeFormacion detalle(UserId actor, Formacion formacion) {
        exigirAdministracion(actor);
        CifrasDePersonas cifras = personas.de(formacion);
        ResumenDeGrupos delosGrupos = grupos.de(formacion);
        return new DetalleDeFormacion(resumen(catalogo.de(formacion)),
                new Personas(cifras.aprendices(), cifras.porEmpezar(), cifras.enCurso(), cifras.terminaron(), cifras.personal()),
                new Grupos(delosGrupos.total(), delosGrupos.conMentor(), delosGrupos.sinMentor(), delosGrupos.recepcion()),
                new PorAtender(delosGrupos.sinMentor(), cifras.aprendicesSinGrupo(), personas.personalSinFormacion()));
    }

    private TarjetaDeFormacion tarjeta(FormacionConfigurada formacion, int personalSinFormacion) {
        CifrasDePersonas cifras = personas.de(formacion.formacion());
        ResumenDeGrupos delosGrupos = grupos.de(formacion.formacion());
        // «Por atender» de una tarjeta no cuenta al personal sin formación: no es de ninguna (se ve en cada detalle y en Equipo y roles).
        int porAtender = delosGrupos.sinMentor() + cifras.aprendicesSinGrupo();
        return new TarjetaDeFormacion(resumen(formacion), cifras.aprendices(), cifras.personal(), delosGrupos.total(), porAtender);
    }

    private static Resumen resumen(FormacionConfigurada f) {
        return new Resumen(f.formacion(), f.nombre(), f.descripcion(), f.activa(), f.duracionesDias(), f.etiquetasDeDuracion(), f.version());
    }

    private void exigirAdministracion(UserId actor) {
        UserSummary quien = usuarios.findById(actor).orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actor));
        if (quien.status() != UserStatus.ACTIVE || !quien.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST ven las formaciones");
        }
    }
}
