package com.renaser.os.users.application.services;

/**
 * El origen al que se le cobra una peticion, y la clave compuesta (origen, correo) con la que
 * cuentan sus limites los endpoints publicos de autenticacion.
 *
 * <p><b>Por que existe (2026-09-21).</b> Hasta ahora el corte duro del login y el del reseteo
 * colgaban de una clave armada SOLO con el correo que venia en el cuerpo de la peticion
 * ({@code "login:email:" + email} y {@code "email:" + email}). Ese contador lo movia cualquiera:
 * diez peticiones anonimas a {@code POST /auth/login} con el correo de otra persona, mas cinco a
 * {@code POST /auth/password/forgot}, la dejaban sin entrar y sin recuperar durante el resto de
 * la ventana — sin cuenta, sin sesion y sin acertar ninguna contrasena. El control puesto para
 * frenar a quien adivina contrasenas se podia volver contra la duena de la cuenta, porque el
 * costo del limite lo pagaba ella y no quien hacia las peticiones.
 *
 * <p><b>La regla que fija esta clase:</b> todo contador cuyo agotamiento RECHAZA una peticion se
 * cuenta contra el origen que la hace. El correo puede entrar en la clave, pero nunca solo:
 * siempre acompanado del origen, para que el cupo de una persona no se pueda gastar desde
 * afuera. Un contador que cuelgue unicamente del correo queda permitido solo si su agotamiento
 * no rechaza nada — por ejemplo, el tope de correos enviados a una casilla en
 * {@code ResetContrasenaService}, que deja de mandar mail pero responde lo mismo de siempre.
 *
 * <p><b>Origen primero, correo despues, y por que ese orden.</b> Una direccion IP no puede
 * contener {@code |}, asi que con el origen adelante el primer {@code |} parte la clave en dos
 * de una sola forma posible. Al reves no: la parte local de un correo entrecomillada admite
 * {@code |} ({@code "a|b"@x.test}), y una clave {@code correo + "|" + ip} se podria fabricar
 * para chocar con la de otro par (correo, origen).
 *
 * <p><b>Origen desconocido.</b> {@code requestIp} es nullable en todos los comandos del modulo.
 * En produccion no llega nula ({@code getRemoteAddr()} siempre devuelve algo, y
 * {@code DireccionIpDelCliente} solo la normaliza), pero si llegara no se puede dejar la
 * peticion sin cobrarle a nadie, ni caer a una clave de solo-correo, que es justamente el
 * agujero que se cierra aca. Todo lo que llega sin origen comparte una unica unidad de conteo,
 * {@link #DESCONOCIDO}: "no se sabe de donde viene" es, a estos efectos, un origen mas.
 */
final class OrigenDeLaPeticion {

    /** Unidad de conteo de todo lo que llega sin IP: una sola, compartida por todo. */
    static final String DESCONOCIDO = "origen-desconocido";

    /** Separador de la clave compuesta. Ver el javadoc de la clase para el orden de las partes. */
    private static final String SEPARADOR = "|";

    private OrigenDeLaPeticion() {
    }

    /** La unidad de conteo de quien hace esta peticion. Nunca devuelve null ni vacio. */
    static String de(String requestIp) {
        return requestIp == null || requestIp.isBlank() ? DESCONOCIDO : requestIp;
    }

    /** Clave {@code prefijo + origen + "|" + correo}, para contar un par (origen, correo). */
    static String claveConEmail(String prefijo, String requestIp, String email) {
        return prefijo + de(requestIp) + SEPARADOR + email;
    }
}
