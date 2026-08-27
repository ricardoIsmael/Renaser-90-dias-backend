package com.renaser.os.users.application.ports.out.accountrequest;

/**
 * Consulta de registros MX de un dominio. Puerto nombrado por intencion, no por tecnologia
 * (CLAUDE.MD §5.4.8): el caso de uso pregunta "¿este dominio recibe correo?" y no sabe que
 * detras hay DNS, ni JNDI, ni un timeout.
 *
 * <p>Devuelve un veredicto cerrado en vez de una lista de registros o una excepcion: traducir
 * "el DNS respondio NXDOMAIN" a "el dominio no existe" es trabajo de infraestructura, y dejarlo
 * en el adaptador es lo que permite testear la regla de negocio sin red.
 */
public interface ResolverMxPort {

    ResultadoMx consultar(String dominio);

    enum ResultadoMx {
        /** El dominio declara al menos un MX: puede recibir correo. */
        TIENE_MX,
        /** El dominio existe pero no declara ningun MX. */
        SIN_MX,
        /** El DNS respondio que el dominio no existe. */
        DOMINIO_INEXISTENTE,
        /** Timeout, SERVFAIL, sin salida a red. No sabemos: no es un "no". */
        INDETERMINADO
    }
}
