package com.renaser.os.shared.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * La direccion IP del cliente, en la forma que Postgres acepta en una columna {@code inet}. Es el UNICO lugar donde el
 * backend decide de quien es una peticion: todo limite por IP y {@code solicitudes_cuenta.ip_solicitud} salen de aca.
 *
 * <h2>De donde sale la IP (E-678)</h2>
 * El backend vive detras de CloudFront y de un nginx transparente (CloudFront -> EC2:8080 -> nginx -> Spring). La
 * conexion TCP que ve Spring es la del nginx, asi que {@code getRemoteAddr()} no dice nada del cliente. El orden es:
 * <ol>
 *   <li>{@code CloudFront-Viewer-Address} ({@code ip:puerto}), <b>solo si esta activada</b> la propiedad
 *       {@code renaser.security.ip-del-cliente.confiar-en-cloudfront-viewer-address} (por defecto no). Esa cabecera
 *       solo es de fiar si la politica de origen de CloudFront la agrega ella misma y pisa la que mande el visitante.</li>
 *   <li>El valor <b>mas a la derecha</b> de {@code X-Forwarded-For}: es el que agrega CloudFront con la IP del
 *       visitante. Lo que va a su izquierda lo escribe el visitante, asi que nunca se usa.</li>
 *   <li>Sin ninguna de las dos (o con un valor que no es una direccion literal), la direccion de la conexion.</li>
 * </ol>
 * {@code Forwarded} y {@code X-Real-IP} no se leen. Spring ya no reescribe la direccion remota desde esas cabeceras:
 * {@code server.forward-headers-strategy} es {@code none} (ver {@code application.yaml}); si volviera a
 * {@code framework}, el filtro de Spring esconderia estas cabeceras antes de llegar aca.
 *
 * <p><b>Supuesto que se documenta, no se verifica en codigo.</b> Las cabeceras solo se leen porque la unica via de
 * entrada al puerto 8080 es CloudFront (el security group solo admite el prefijo de CloudFront y una IP fija). Un
 * proceso que llegara al 8080 por otro camino podria escribir tambien el ultimo valor.
 *
 * <h2>Forma de la direccion (E-151)</h2>
 * Postgres rechaza en {@code inet} un IPv6 entre corchetes ({@code invalid input syntax for type inet:
 * "[2803:9810:...]"}), con puerto o con identificador de zona. Se normaliza aca, en el borde, y no en cada repositorio,
 * para que los contadores y la columna vean siempre el mismo texto.
 *
 * <p><b>Lo que esta clase NO responde.</b> Devuelve los 128 bits enteros: la direccion para GUARDAR o mostrar. La
 * unidad que los limites por IP CUENTAN es otra (el /64 en IPv6) y vive en
 * {@link com.renaser.os.shared.domain.UnidadDeConteoPorIp}.
 */
public final class DireccionIpDelCliente {

    static final String CABECERA_DEL_VISITANTE_DE_CLOUDFRONT = "CloudFront-Viewer-Address";
    static final String CABECERA_REENVIADO = "X-Forwarded-For";

    /** Se fija una vez al arrancar ({@link DireccionIpDelClienteConfig}); por defecto, la opcion que no confia en esa cabecera. */
    private static volatile boolean confiarEnLaCabeceraDelVisitante = false;

    private DireccionIpDelCliente() {
    }

    /**
     * La IP de quien hace esta peticion, entera, lista para guardar en una columna {@code inet}. Para CONTAR limites por
     * IP no se usa esto sino {@link com.renaser.os.shared.domain.UnidadDeConteoPorIp#de(String)}.
     */
    public static String de(HttpServletRequest request) {
        return de(request, confiarEnLaCabeceraDelVisitante);
    }

    static String de(HttpServletRequest request, boolean confiarEnCabeceraDelVisitante) {
        if (confiarEnCabeceraDelVisitante) {
            String delVisitante = comoDireccion(request.getHeader(CABECERA_DEL_VISITANTE_DE_CLOUDFRONT));
            if (delVisitante != null) {
                return delVisitante;
            }
        }
        String agregadaPorCloudFront = comoDireccion(ultimoValorDeReenviado(request));
        if (agregadaPorCloudFront != null) {
            return agregadaPorCloudFront;
        }
        return normalizar(request.getRemoteAddr());
    }

    /** Lo fija {@link DireccionIpDelClienteConfig} desde la propiedad; las pruebas usan la sobrecarga con el valor explicito. */
    static void confiarEnLaCabeceraDelVisitante(boolean confiar) {
        confiarEnLaCabeceraDelVisitante = confiar;
    }

    /**
     * El valor mas a la derecha de {@code X-Forwarded-For}: el ultimo de la ultima linea de cabecera (una peticion puede
     * traer varias lineas) y el ultimo de los separados por coma. Null si no hay ninguno.
     */
    private static String ultimoValorDeReenviado(HttpServletRequest request) {
        var lineas = request.getHeaders(CABECERA_REENVIADO);
        if (lineas == null) {
            return null;
        }
        List<String> valores = new ArrayList<>();
        for (String linea : Collections.list(lineas)) {
            if (linea == null) {
                continue;
            }
            for (String valor : linea.split(",")) {
                if (!valor.isBlank()) {
                    valores.add(valor.trim());
                }
            }
        }
        return valores.isEmpty() ? null : valores.get(valores.size() - 1);
    }

    /**
     * Lee una direccion literal de lo que traen estas cabeceras: {@code 198.51.100.10}, {@code 198.51.100.10:46532},
     * {@code [2001:db8::1]:46532}, {@code 2001:db8::1:46532} o {@code 2001:db8::1}. Devuelve la direccion sin puerto,
     * corchetes ni zona, o null si el texto no es una direccion.
     *
     * <p>Un IPv6 sin corchetes y con puerto es ambiguo con un IPv6 sin puerto: se prueba primero como direccion con
     * puerto (lo que documenta CloudFront para su cabecera: siempre trae puerto) y despues entera.
     *
     * <p>Se parsea con {@link InetAddress#ofLiteral}, que nunca resuelve nombres: el texto lo manda quien hace la
     * peticion y {@code getByName} se volveria una consulta DNS gobernada por ese tercero.
     */
    static String comoDireccion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        if (texto.startsWith("[")) {
            int cierre = texto.indexOf(']');
            return cierre > 1 ? literalOnull(texto.substring(1, cierre)) : null;
        }
        int primerosDosPuntos = texto.indexOf(':');
        if (primerosDosPuntos < 0) {
            return literalOnull(texto);
        }
        if (primerosDosPuntos == texto.lastIndexOf(':')) {
            return literalOnull(texto.substring(0, primerosDosPuntos));
        }
        int ultimosDosPuntos = texto.lastIndexOf(':');
        String conPuerto = literalOnull(texto.substring(0, ultimosDosPuntos));
        return conPuerto != null && soloDigitos(texto.substring(ultimosDosPuntos + 1)) ? conPuerto : literalOnull(texto);
    }

    private static boolean soloDigitos(String texto) {
        return !texto.isEmpty() && texto.chars().allMatch(Character::isDigit);
    }

    private static String literalOnull(String candidato) {
        String limpio = normalizar(candidato);
        if (limpio == null || limpio.isBlank()) {
            return null;
        }
        try {
            InetAddress.ofLiteral(limpio);
            return limpio;
        } catch (IllegalArgumentException noEsUnaDireccion) {
            return null;
        }
    }

    /**
     * Saca los corchetes de un IPv6 y el identificador de zona si viniera ({@code fe80::1%eth0}), que {@code inet}
     * tampoco acepta. Un IPv4 pasa intacto.
     *
     * <p>Deliberadamente no valida que sea una IP: si llegara algo que no lo es, quien decide que hacer es la base (la
     * columna es {@code inet}), no este metodo. Su unico trabajo es no romper una direccion que si era valida.
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
