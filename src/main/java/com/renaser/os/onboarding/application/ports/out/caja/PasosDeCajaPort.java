package com.renaser.os.onboarding.application.ports.out.caja;

import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Los pasos de la Caja Renaser, en {@code etapas_onboarding_completadas} con {@code flujo = 'caja:<n>:<paso>'}
 * (V82). La PK (usuario, flujo) es la que hace que un doble toque choque en vez de duplicar un paso.
 */
public interface PasosDeCajaPort {

    List<PasoDeCaja> deAprendiz(UserId aprendizId);

    /** Los de varios aprendices en UNA consulta (la lista del Admin, el barrido). Sin clave = sin pasos. */
    Map<UserId, List<PasoDeCaja>> deAprendices(Collection<UserId> aprendices);

    /**
     * Guarda un paso nuevo.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException si ese paso ya estaba (409)
     */
    void registrar(PasoDeCaja paso);

    /** Guarda el paso o reemplaza el que había con la misma clave (las fotos, que se pueden cambiar). */
    void reemplazar(PasoDeCaja paso);

    /** Guarda el paso solo si no estaba. @return si lo guardó (las marcas de aviso del barrido) */
    boolean registrarSiFalta(PasoDeCaja paso);
}
