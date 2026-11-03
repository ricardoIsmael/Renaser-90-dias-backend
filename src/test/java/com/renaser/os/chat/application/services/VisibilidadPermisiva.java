package com.renaser.os.chat.application.services;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;

/**
 * Una {@link VisibilidadPorFormacion} para las pruebas que no hablan de formaciones (spec 024): todos ven todo y todos los tipos de comunidad están
 * prendidos, así que nada se filtra. Las pruebas del aislamiento arman la real con sus dobles.
 */
public class VisibilidadPermisiva extends VisibilidadPorFormacion {

    public VisibilidadPermisiva() {
        super(null, null, null, null);
    }

    @Override
    public AlcanceDeFormacion alcanceDe(UserId actor, Formacion pedida) {
        return AlcanceDeFormacion.todas().acotadoA(pedida);
    }

    @Override
    public boolean tipoPrendido(Formacion formacion, TipoDeComunidad tipo) {
        return true;
    }

    @Override
    public boolean puedeVer(Conversacion conversacion, UserId actor) {
        return true;
    }

    @Override
    public void exigirQueLaVea(Conversacion conversacion, UserId actor) {
        // todos ven todo
    }

    @Override
    public List<Conversacion> visiblesPara(Collection<Conversacion> conversaciones, UserId actor, AlcanceDeFormacion alcance) {
        return List.copyOf(conversaciones);
    }
}
