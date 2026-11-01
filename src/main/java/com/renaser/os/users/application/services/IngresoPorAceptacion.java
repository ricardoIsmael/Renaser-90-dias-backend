package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaAlAceptar;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.accountrequest.EleccionDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.PedidoDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.ReglasDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.ReglasDeIngreso.Resultado;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

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
    /** Hasta integrar la 026 (ficha con diagnóstico) nadie la implementa en esta rama: sin ella, «con diagnóstico» se rechaza con un aviso claro. */
    private final ObjectProvider<FichaAlAceptar> ficha;
    private final Clock clock;

    IngresoPorAceptacion(CatalogoDeFormacionesPort catalogo, SaveParticipacionProgramaPort participaciones,
                         FormacionesDelPersonalPort personal, ObjectProvider<FichaAlAceptar> ficha, Clock clock) {
        this.catalogo = catalogo;
        this.participaciones = participaciones;
        this.personal = personal;
        this.ficha = ficha;
        this.clock = clock;
    }

    /**
     * @param rolFinal el rol con el que la cuenta quedó (el base, si tiene uno personalizado)
     * @return lo que se decidió, para guardarlo en la solicitud
     * @throws IllegalArgumentException (400) si el pedido no cumple las reglas del rol
     * @throws IllegalStateException    (409) si se pide el diagnóstico y no hay uno publicado
     */
    EleccionDeIngreso formalizar(UserId persona, UserId actor, UserRole rolFinal, PedidoDeIngreso pedido) {
        Resultado resultado = ReglasDeIngreso.decidir(rolFinal, pedido, catalogoPorFormacion());
        participaciones.save(ParticipacionPrograma.inscribirTraineeAprobado(persona, clock, resultado.formacionDelPrograma(),
                resultado.duracionDelProgramaDias()));
        if (ReglaDeAlcance.esPersonalConFormaciones(rolFinal)) {
            personal.reemplazar(persona, resultado.eleccion().formaciones(), resultado.formacionDelPrograma(), actor);
        }
        if (pedido.conDiagnostico()) {
            asignarDiagnostico(persona, actor);
        }
        return resultado.eleccion();
    }

    /** ¿Se puede ofrecer «Con diagnóstico»? Sin la ficha con diagnóstico instalada, o sin uno publicado, no. */
    boolean diagnosticoDisponible() {
        FichaAlAceptar disponible = ficha.getIfAvailable();
        return disponible != null && disponible.hayDiagnosticoPublicado();
    }

    private void asignarDiagnostico(UserId persona, UserId actor) {
        if (!diagnosticoDisponible()) {
            throw new IllegalStateException("Todavía no hay un diagnóstico publicado: acepta a la persona sin diagnóstico.");
        }
        ficha.getObject().conDiagnostico(persona, actor);
    }

    private Map<Formacion, FormacionConfigurada> catalogoPorFormacion() {
        Map<Formacion, FormacionConfigurada> porFormacion = new EnumMap<>(Formacion.class);
        catalogo.todas().forEach(configurada -> porFormacion.put(configurada.formacion(), configurada));
        return porFormacion;
    }
}
