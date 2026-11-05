package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;

public interface SaveCuentaGooglePort {

    /** Guarda la cuenta (el permiso permanente va cifrado). Reemplaza a la que hubiera. */
    CuentaDeGoogle guardar(CuentaDeGoogle cuenta);

    /** Desconecta: borra la cuenta y su permiso. */
    void borrar();
}
