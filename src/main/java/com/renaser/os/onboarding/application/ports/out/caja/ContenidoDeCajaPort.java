package com.renaser.os.onboarding.application.ports.out.caja;

import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Set;

/**
 * El contenido de la caja: la lista vigente (las opciones de la pregunta {@code caja_contenido}) y el
 * checklist de cada aprendiz (su respuesta a esa pregunta, un array en {@code valor_json}).
 */
public interface ContenidoDeCajaPort {

    ContenidoDeCaja vigente();

    /** Reescribe la lista entera: los checklists ya marcados no se tocan. */
    void reemplazar(ContenidoDeCaja contenido);

    Set<String> marcadosDe(UserId aprendizId);

    void guardarMarcados(UserId aprendizId, Set<String> marcados, Instant en);
}
