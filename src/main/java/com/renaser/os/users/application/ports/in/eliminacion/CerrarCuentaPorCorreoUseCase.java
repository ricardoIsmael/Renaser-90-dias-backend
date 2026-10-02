package com.renaser.os.users.application.ports.in.eliminacion;

import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

/**
 * Pedir la eliminacion desde la pagina web publica, sin sesion (decision 4 del dueño, D-243; Google
 * Play exige un enlace web). Se prueba que el correo es de quien lo pide con un codigo que llega a
 * ese correo. No revela si el correo tiene cuenta: pedir el codigo responde igual siempre, y un
 * codigo equivocado falla igual exista o no la cuenta.
 */
public interface CerrarCuentaPorCorreoUseCase {

    void solicitarCodigo(String email, String requestIp);

    EstadoBajaCuenta confirmar(String email, String codigo, String requestIp);
}
