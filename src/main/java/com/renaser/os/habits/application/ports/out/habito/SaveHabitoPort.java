package com.renaser.os.habits.application.ports.out.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;

public interface SaveHabitoPort {

    Habito save(Habito habito);

    /**
     * Borrado fisico (panel admin, hueco #11) — solo tiene exito si nada referencia el
     * habito todavia: {@code registros_habito.habito_id} es {@code ON DELETE RESTRICT}
     * (P-02, "el catalogo NO arrastra historial"), asi que Postgres rechaza el DELETE con
     * una violacion de integridad si ya existe algun track. El adaptador no verifica esto
     * a mano: deja que la constraint de la base haga el trabajo y el
     * {@code GlobalExceptionHandler} traduce esa violacion a 409 (ver su javadoc sobre
     * {@code DataIntegrityViolationException}). Un habito sin historial se puede borrar de
     * verdad, sea un error de carga; uno con historial se da de baja logica con
     * {@link com.renaser.os.habits.domain.model.habito.Habito#desactivar}.
     */
    void eliminar(HabitoId id);
}
