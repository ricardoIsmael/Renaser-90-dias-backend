package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/** Lee y guarda los tipos de comunidad prendidos de una formación (columna {@code formaciones.tipos_de_comunidad}, V138). */
public interface TiposDeComunidadPort {

    Set<TipoDeComunidad> prendidosDe(Formacion formacion);

    /** Guarda el conjunto completo de tipos prendidos; quién y cuándo lo cambió queda en la fila de la formación. */
    void guardar(Formacion formacion, Set<TipoDeComunidad> prendidos, UserId actor);
}
