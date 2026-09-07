package com.renaser.os.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La direccion IP del cliente, en la forma que Postgres acepta en una columna {@code inet}.
 *
 * <p><b>Por que existe (E-151, 2026-09-07).</b> Desde que el backend confia en
 * {@code X-Forwarded-For} ({@code server.forward-headers-strategy=framework}, E-149),
 * {@code getRemoteAddr()} devuelve la IP real del aprendiz y ya no la de CloudFront. Con IPv4 no
 * cambio nada. Con <b>IPv6</b> si: el {@code ForwardedHeaderFilter} de Spring reconstruye la
 * direccion como <i>host de URI</i>, y en un URI un IPv6 va <b>entre corchetes</b>. O sea que
 * {@code getRemoteAddr()} empezo a devolver {@code [2803:9810:...]}.
 *
 * <p>Postgres rechaza eso: {@code invalid input syntax for type inet: "[2803:9810:...]"}. La
 * consecuencia real fue que <b>toda persona con conexion IPv6 no podia registrarse</b>: el alta
 * explotaba al insertar {@code solicitudes_cuenta.ip_solicitud}. En IPv4 seguia funcionando, que
 * es justo lo que hace que el problema pase desapercibido en las pruebas de quien lo despliega.
 *
 * <p>Se normaliza aca, en el borde, y no en cada repositorio: la IP entra al sistema por nueve
 * lugares (alta, login, reset, verificacion de correo, social) y varios la usan para contar
 * limites por IP. Si se normalizara en un solo repositorio, los contadores compararian
 * {@code [2803:...]} contra {@code 2803:...} y el limite por IP dejaria de acertar sin avisar.
 */
public final class DireccionIpDelCliente {

    private DireccionIpDelCliente() {
    }

    /** La IP de quien hace esta peticion, lista para guardar o comparar. */
    public static String de(HttpServletRequest request) {
        return normalizar(request.getRemoteAddr());
    }

    /**
     * Saca los corchetes de un IPv6 y el identificador de zona si viniera
     * ({@code fe80::1%eth0}), que {@code inet} tampoco acepta. Un IPv4 pasa intacto.
     *
     * <p>Deliberadamente no valida que sea una IP: si llegara algo que no lo es, quien decide
     * que hacer es la base (la columna es {@code inet}), no este metodo. Su unico trabajo es no
     * romper una direccion que si era valida.
     */
    static String normalizar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return direccion;
        }
        String limpia = direccion.trim();
        if (limpia.startsWith("[")) {
            int cierre = limpia.indexOf(']');
            if (cierre > 1) {
                limpia = limpia.substring(1, cierre);
            }
        }
        int zona = limpia.indexOf('%');
        if (zona > 0) {
            limpia = limpia.substring(0, zona);
        }
        return limpia;
    }
}
