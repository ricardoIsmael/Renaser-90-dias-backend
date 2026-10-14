package com.renaser.os.users.application.ports.out.user;

/**
 * Serializa la creación y la reparación de las cuentas de prueba de «ver como» (D-270) entre instancias: dos Soporte TI
 * que abren «ver como Mentor» a la vez no pueden crear dos cuentas con el mismo correo (la segunda chocaría con el
 * UNIQUE y respondería 409). Vale hasta el final de la transacción.
 */
public interface BloquearCuentasDePruebaPort {

    void bloquear();
}
