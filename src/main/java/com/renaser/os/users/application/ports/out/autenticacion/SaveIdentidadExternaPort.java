package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;

public interface SaveIdentidadExternaPort {

    /**
     * Inserta el vinculo {@code (proveedor, sujetoProveedor) -> usuarioId}. La FK de
     * {@code identidades_externas.usuario_id} exige que la fila de {@code usuarios} ya exista —
     * este metodo solo puede llamarse DESPUES de que el {@code User} este creado, nunca antes
     * (ver la pregunta abierta sobre el momento de vinculacion en docs/MODULO_AUTH.md §10).
     */
    void guardar(IdentidadExterna identidad);
}
