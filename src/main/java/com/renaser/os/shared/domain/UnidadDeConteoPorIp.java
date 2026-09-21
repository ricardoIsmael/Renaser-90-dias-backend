package com.renaser.os.shared.domain;

import java.net.InetAddress;

/**
 * La unidad que los limites por IP tienen que CONTAR, que no es la misma que la que se GUARDA.
 *
 * <p><b>Por que existe.</b> Los cinco contadores por IP del modulo {@code users} (alta, login,
 * reseteo, verificacion de correo y consulta de correo) armaban su clave de Redis con la
 * direccion textual entera que devuelve {@code DireccionIpDelCliente.de}. En IPv4 eso identifica
 * razonablemente a un abonado. En IPv6 no: a un movil se le asigna un /64 propio por sesion y a
 * una conexion domestica se le delega un /64 o un /56, asi que el mismo cliente puede originar
 * cada peticion desde una direccion distinta <b>que le pertenece de verdad</b>, sin falsificar
 * ninguna cabecera y sin pasar por ningun proxy. Cada direccion estrenaba su propio contador:
 * los cinco limites contaban uno por peticion y no disparaban nunca. El caso peor era
 * {@code POST /account-requests/exists}, cuyo unico control es ese contador y que responde con
 * un booleano si un correo ya tiene cuenta — enumeracion del padron sin tope.
 *
 * <h2>Por que /64 y no /128, /56 o /48</h2>
 * <ul>
 *   <li><b>/128 (lo que habia)</b> cuenta interfaces, no personas. Los 64 bits bajos son el
 *       identificador de interfaz (RFC 4291 sec. 2.5.1): los elige el propio host, las extensiones
 *       de privacidad (RFC 8981) los rotan solas cada pocas horas, y una maquina puede enlazar
 *       2^64 direcciones sin pedirle permiso a nadie. Contar algo que el cliente acuña gratis no
 *       es contar.</li>
 *   <li><b>/64</b> es a la vez la unidad mas chica que el cliente <i>no</i> puede multiplicar
 *       gratis y la mas grande que todavia se puede afirmar de un solo abonado: la arquitectura
 *       de direccionamiento fija el /64 como tamaño de subred, y 3GPP le asigna exactamente un
 *       /64 a cada sesion movil — que es el acceso que la bitacora registra como habitual entre
 *       los usuarios de este producto (E-151: IPv6 "comun en datos moviles" en Peru y Bolivia).
 *       Por eso es el prefijo elegido.</li>
 *   <li><b>/56 o /48</b> apretarian mas — atraparian tambien al abonado domestico que rota entre
 *       los /64 de su prefijo delegado — pero meten desconocidos en el mismo cubo: un operador
 *       movil reparte /64 de distintos abonados dentro de un mismo /48, asi que un solo abusador
 *       consumiria la cuota de gente que no tiene nada que ver. Eso es E-151 otra vez (personas
 *       reales que no pueden entrar), y lo pagarian justo los usuarios que la bitacora nombra.</li>
 * </ul>
 *
 * <p><b>Lo que queda vivo a proposito:</b> quien tenga un /56 delegado puede multiplicar el
 * limite por 256 usando sus 256 /64 (por 65536 con un /48). Es un techo acotado donde hoy no
 * habia ninguno. Si alguna vez hay que apretar mas, se cambia {@link #BITS_DE_PREFIJO_IPV6} y
 * nada mas — pero se decide mirando como delega prefijos el operador real, no a ojo.
 *
 * <p><b>Por que hay que canonicalizar el texto.</b> {@code 2803:9810:0:0::1},
 * {@code 2803:9810::1}, {@code 2803:9810::0001}, {@code 2803:9810::A} y {@code [2803:9810::1]}
 * son la misma direccion; mientras la clave fue la cadena cruda, cada forma de escribirla era un
 * contador nuevo y gratis. Lo mismo entre {@code ::ffff:1.2.3.4} y {@code 1.2.3.4}.
 *
 * <p><b>Por que {@code ofLiteral} y nunca {@code InetAddress.getByName}.</b> {@code getByName}
 * resuelve DNS cuando el texto no es una direccion literal, y este texto lo elige el cliente
 * (llega por {@code X-Forwarded-For}): cada conteo se volveria una consulta DNS gobernada por
 * quien se quiere limitar. {@code InetAddress.ofLiteral} (Java 22+) parsea el literal y lanza
 * {@code IllegalArgumentException} con cualquier otra cosa — comprobado: {@code "localhost"}
 * lanza en vez de resolver.
 *
 * <p><b>IPv4 no cambia.</b> Sigue contando por direccion entera, y para una direccion ya
 * canonica el texto de salida es identico al de entrada: las claves de Redis que ya estan
 * contando no se reinician con el despliegue.
 *
 * <p><b>Lo que esto NO arregla</b>, y hay que hacer aparte: que los servicios no cuenten nada
 * cuando la IP llega nula, y la pregunta de si se puede confiar en {@code X-Forwarded-For}
 * (agregar por prefijo no impide que un cliente escriba su propia cabecera).
 */
public final class UnidadDeConteoPorIp {

    /** Ver la discusion de la clase: /64 es el prefijo elegido, y este es el unico lugar donde vive. */
    static final int BITS_DE_PREFIJO_IPV6 = 64;

    private static final int GRUPOS_IPV6 = 8;
    private static final int GRUPOS_DEL_PREFIJO = BITS_DE_PREFIJO_IPV6 / 16;

    private UnidadDeConteoPorIp() {
    }

    /**
     * El cubo en el que se cuenta esta direccion: la direccion canonica entera si es IPv4, y el
     * prefijo {@code .../64} si es IPv6. Lo que no sea una direccion literal se devuelve tal
     * cual y se cuenta opaco, igual que antes de este cambio.
     *
     * @param direccion la direccion tal como la deja {@code DireccionIpDelCliente.de}; puede
     *                  ser nula. Los corchetes y el identificador de zona se toleran igual —
     *                  la unidad de conteo no puede depender de que el llamador haya limpiado
     *                  antes, que es la clase de suposicion que produjo este hallazgo.
     */
    public static String de(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return direccion;
        }
        byte[] octetos = literalAOctetos(sinZona(direccion.trim()));
        if (octetos == null) {
            return direccion;
        }
        if (octetos.length == 4) {
            return textoIpv4(octetos);
        }
        if (esIpv4Mapeada(octetos)) {
            // ::ffff:1.2.3.4 es 1.2.3.4. Agregarla por /64 la meteria en el cubo ::/64 junto con
            // TODAS las demas direcciones mapeadas: un solo contador global para todo IPv4, que
            // es exactamente el hueco nuevo que este arreglo no puede abrir.
            return textoIpv4(new byte[] {octetos[12], octetos[13], octetos[14], octetos[15]});
        }
        return prefijoIpv6(octetos);
    }

    /** {@code fe80::1%eth0} y {@code fe80::1} son la misma direccion y tienen que contar juntas. */
    private static String sinZona(String direccion) {
        int zona = direccion.indexOf('%');
        return zona > 0 ? direccion.substring(0, zona) : direccion;
    }

    /**
     * Parsea el literal sin salir a la red. Devuelve null si el texto no es una direccion, que es
     * el caso que se cuenta opaco.
     */
    private static byte[] literalAOctetos(String literal) {
        try {
            return InetAddress.ofLiteral(literal).getAddress();
        } catch (IllegalArgumentException noEsUnaDireccion) {
            return null;
        }
    }

    private static boolean esIpv4Mapeada(byte[] octetos) {
        for (int i = 0; i < 10; i++) {
            if (octetos[i] != 0) {
                return false;
            }
        }
        return octetos[10] == (byte) 0xFF && octetos[11] == (byte) 0xFF;
    }

    private static String textoIpv4(byte[] octetos) {
        return (octetos[0] & 0xFF) + "." + (octetos[1] & 0xFF) + "."
                + (octetos[2] & 0xFF) + "." + (octetos[3] & 0xFF);
    }

    /**
     * Enmascara todo lo que esta por debajo del prefijo: los grupos que no se copian quedan en
     * cero, que es justamente la mascara.
     */
    private static String prefijoIpv6(byte[] octetos) {
        int[] grupos = new int[GRUPOS_IPV6];
        for (int i = 0; i < GRUPOS_DEL_PREFIJO; i++) {
            grupos[i] = ((octetos[2 * i] & 0xFF) << 8) | (octetos[2 * i + 1] & 0xFF);
        }
        return comprimir(grupos) + "/" + BITS_DE_PREFIJO_IPV6;
    }

    /**
     * Forma comprimida de RFC 5952: minusculas, sin ceros a la izquierda y con {@code ::} sobre
     * la racha de ceros mas larga (la de mas a la izquierda si empatan). No es por estetica: el
     * texto es la clave del contador, asi que tiene que ser uno solo por prefijo.
     */
    private static String comprimir(int[] grupos) {
        int inicioMejor = -1;
        int largoMejor = 0;
        int inicio = -1;
        int largo = 0;
        for (int i = 0; i < grupos.length; i++) {
            if (grupos[i] != 0) {
                inicio = -1;
                largo = 0;
                continue;
            }
            if (inicio < 0) {
                inicio = i;
            }
            largo++;
            if (largo > largoMejor) {
                largoMejor = largo;
                inicioMejor = inicio;
            }
        }

        StringBuilder texto = new StringBuilder();
        int i = 0;
        while (i < grupos.length) {
            if (i == inicioMejor && largoMejor > 1) {
                texto.append("::");
                i += largoMejor;
                continue;
            }
            if (!texto.isEmpty() && texto.charAt(texto.length() - 1) != ':') {
                texto.append(':');
            }
            texto.append(Integer.toHexString(grupos[i]));
            i++;
        }
        return texto.isEmpty() ? "::" : texto.toString();
    }
}
