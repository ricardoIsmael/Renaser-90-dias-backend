package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaAlAceptar;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.TerminosPublicados;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.domain.model.accountrequest.EleccionDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.PedidoDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.ReglasDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.ReglasDeIngreso.Resultado;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Deja a la persona que se acepta dentro de su formación (spec 025, FR-008, FR-009): decide con {@link ReglasDeIngreso}, la inscribe en el programa con
 * la formación y la duración elegidas (el reloj queda en pausa hasta que elija su Día 1), asigna sus formaciones si es personal y le pone la ficha con
 * diagnóstico si se pidió. No abre transacción: corre dentro de la de aceptar, así que todo se queda o nada se queda.
 */
@Component
class IngresoPorAceptacion {

    private final CatalogoDeFormacionesPort catalogo;
    private final SaveParticipacionProgramaPort participaciones;
    private final FormacionesDelPersonalPort personal;
    private final CargarRolesPersonalizadosPort rolesPersonalizados;
    /** La ficha con diagnóstico (spec 026): la implementa {@code onboarding}. Sin un diagnóstico publicado, «con diagnóstico» se rechaza con un aviso claro. */
    private final FichaAlAceptar ficha;
    /** Spec 027: los términos editables los implementa {@code onboarding}; una formación sin términos publicados no recibe personas nuevas. */
    private final TerminosPublicados terminos;
    private final Clock clock;

    IngresoPorAceptacion(CatalogoDeFormacionesPort catalogo, SaveParticipacionProgramaPort participaciones,
                         FormacionesDelPersonalPort personal, CargarRolesPersonalizadosPort rolesPersonalizados,
                         FichaAlAceptar ficha, TerminosPublicados terminos, Clock clock) {
        this.catalogo = catalogo;
        this.participaciones = participaciones;
        this.personal = personal;
        this.rolesPersonalizados = rolesPersonalizados;
        this.ficha = ficha;
        this.terminos = terminos;
        this.clock = clock;
    }

    /**
     * @param rolFinal            el rol con el que la cuenta quedó (el base, si tiene uno personalizado)
     * @param rolPersonalizadoId  el rol personalizado con el que se aceptó, si fue con uno: la foto guarda su id y su nombre de este momento
     * @return lo que se decidió, para guardarlo en la solicitud
     * @throws IllegalArgumentException (400) si el pedido no cumple las reglas del rol
     * @throws IllegalStateException    (409) si se pide el diagnóstico y no hay uno publicado
     */
    EleccionDeIngreso formalizar(UserId persona, UserId actor, UserRole rolFinal, PedidoDeIngreso pedido, UUID rolPersonalizadoId) {
        Resultado resultado = ReglasDeIngreso.decidir(rolFinal, pedido, catalogoPorFormacion());
        exigirTerminosPublicados(resultado.eleccion().formaciones());
        participaciones.save(ParticipacionPrograma.inscribirTraineeAprobado(persona, clock, resultado.formacionDelPrograma(),
                resultado.duracionDelProgramaDias()));
        if (ReglaDeAlcance.esPersonalConFormaciones(rolFinal)) {
            personal.reemplazar(persona, resultado.eleccion().formaciones(), resultado.formacionDelPrograma(), actor);
        }
        if (pedido.conDiagnostico()) {
            asignarDiagnostico(persona, actor);
        }
        return conSuRolPersonalizado(resultado.eleccion(), rolPersonalizadoId);
    }

    private EleccionDeIngreso conSuRolPersonalizado(EleccionDeIngreso eleccion, UUID rolPersonalizadoId) {
        if (rolPersonalizadoId == null) {
            return eleccion;
        }
        String nombre = rolesPersonalizados.porId(rolPersonalizadoId).map(RolPersonalizado::nombre)
                .orElseThrow(() -> new IllegalStateException("El rol personalizado con el que se acepta ya no existe."));
        return eleccion.conRolPersonalizado(rolPersonalizadoId, nombre);
    }

    /** Spec 027, FR-021: una formación sin términos publicados no recibe personas nuevas (nadie podría aceptarlos). */
    private void exigirTerminosPublicados(Iterable<Formacion> formaciones) {
        for (Formacion formacion : formaciones) {
            if (!terminos.estanPublicados(formacion)) {
                throw new IllegalStateException("«" + catalogoPorFormacion().get(formacion).nombre()
                        + "» todavía no tiene términos y condiciones publicados. Publícalos antes de aceptar a alguien ahí.");
            }
        }
    }

    /** ¿Tiene ya términos publicados esa formación? */
    boolean tieneTerminos(Formacion formacion) {
        return terminos.estanPublicados(formacion);
    }

    /** ¿Se puede ofrecer «Con diagnóstico»? Solo si hay un diagnóstico publicado. */
    boolean diagnosticoDisponible() {
        return ficha.hayDiagnosticoPublicado();
    }

    private void asignarDiagnostico(UserId persona, UserId actor) {
        if (!diagnosticoDisponible()) {
            throw new IllegalStateException("Todavía no hay un diagnóstico publicado: acepta a la persona sin diagnóstico.");
        }
        ficha.conDiagnostico(persona, actor);
    }

    private Map<Formacion, FormacionConfigurada> catalogoPorFormacion() {
        Map<Formacion, FormacionConfigurada> porFormacion = new EnumMap<>(Formacion.class);
        catalogo.todas().forEach(configurada -> porFormacion.put(configurada.formacion(), configurada));
        return porFormacion;
    }
}
