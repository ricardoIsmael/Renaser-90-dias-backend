package com.renaser.os.users.domain.model.user;

import java.util.Locale;
import java.util.regex.Pattern;


public record Email(String value) {

    /**
     * La parte de dominio ya no admite ':' ni '/'. Eran los dos caracteres que dejaban pasar un
     * nombre con forma de URL ({@code a@ldap://host:1389/x}) hasta el resolvedor MX, donde el JDK
     * lo toma como esquema JNDI y sale a conectarse al host y al puerto que vinieron en el texto.
     * Sin ':' no hay esquema que extraer. La parte local queda permisiva como estaba: no viaja a
     * ningun resolvedor y ahi el formato tiene que ser ancho.
     *
     * <p>Esta es la segunda reja, no la primera: la que de verdad acota el nombre de host vive en
     * {@code DnsResolverMxAdapter}, que es donde esta el interprete.
     */
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s:/]+\\.[^@\\s:/]{2,}$");

    public Email {
        value = normalize(value);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El email no puede ser vacio");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            // Sin el valor: este mensaje termina en el log Y en el cuerpo HTTP via
            // GlobalExceptionHandler.respond, y CLAUDE.MD §5.4.9 prohibe loguear correos
            // completos. Quien depura tiene el request; el log no necesita el dato personal.
            throw new IllegalArgumentException("Formato de email invalido");
        }
        return normalized;
    }

    /**
     * Parte de dominio, ya normalizada a minusculas. Es donde se pregunta si el correo puede
     * entregarse: los registros MX son del dominio, no del buzon.
     *
     * <p>Lo que {@link #normalize} garantiza de este valor es acotado y conviene no exagerarlo:
     * que hay exactamente una arroba, que no hay espacios, que hay un punto con al menos dos
     * caracteres detras y que no hay ni ':' ni '/'. NO garantiza que sea un nombre de host
     * resoluble. Quien lo entregue a un resolvedor tiene que validarlo como host por su cuenta.
     */
    public String dominio() {
        return value.substring(value.indexOf('@') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
