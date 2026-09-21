package com.renaser.os.users.infrastructure.adapter.out.dns;

import com.renaser.os.users.application.ports.out.accountrequest.ResolverMxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Consulta MX por DNS con el proveedor JNDI que ya trae el JDK — sin dependencias nuevas. Es el
 * equivalente del {@code dns.resolveMx} de Node que usaba el repo viejo.
 *
 * <p><b>El destino de la consulta lo fija el servidor, nunca quien escribe el correo.</b>
 * {@code getAttributes} NO es un cliente de DNS: antes de usar el contexto de
 * {@link #configuracion()} mira si el nombre empieza con un esquema de URL y, si lo hay, encamina
 * la llamada al proveedor JNDI de ese esquema. Con {@code ldap://host:1389/x} el JDK abre una
 * conexion TCP al host y al puerto que vinieron en el texto. Por eso el nombre pasa antes por
 * {@link #NOMBRE_DE_HOST}: es la reja que devuelve la eleccion del destino al servidor.
 *
 * <p><b>Timeout obligatorio.</b> Sin el, un DNS que no responde bloquea el hilo de la request
 * hasta el timeout del sistema operativo. El aviso de dominio es informativo y no bloquea el
 * registro, asi que no vale la pena hacer esperar mas de unos segundos: pasado el limite se
 * responde {@code INDETERMINADO}, que el caso de uso trata como "no sabemos", nunca como un "no".
 *
 * <p>{@code com.sun.jndi.dns.timeout.retries=1}: sin esto JNDI reintenta cuatro veces y el
 * tiempo real de espera termina siendo varias veces el configurado.
 */
@Component
public class DnsResolverMxAdapter implements ResolverMxPort {

    private static final Logger log = LoggerFactory.getLogger(DnsResolverMxAdapter.class);

    private static final String[] SOLO_MX = {"MX"};

    /**
     * Un nombre de host y nada mas: etiquetas alfanumericas con guiones interiores, separadas por
     * puntos, al menos dos etiquetas y 253 caracteres como maximo.
     *
     * <p>Existe porque el formato de {@code Email} se escribio para separar buzon de dominio, no
     * para acotar un host: admite ':' y '/', que es justo lo que {@code getAttributes} necesita
     * para encaminar el nombre a otro proveedor JNDI en vez de al DNS. Al no admitir ninguno de
     * los dos, aca no queda ningun esquema que el JDK pueda extraer, y el unico contexto posible
     * es el DNS de {@link #configuracion()}.
     *
     * <p>{@code CASE_INSENSITIVE} a proposito: los nombres de DNS no distinguen mayusculas, y el
     * puerto no le exige minusculas a quien lo llama. Hoy el unico llamador entrega el dominio ya
     * normalizado por {@code Email}, pero un llamador futuro que pasara {@code Renaser.com} se
     * llevaria un "ese dominio no existe" que es falso. No ensancha la reja: ':' y '/' siguen
     * fuera de todas las clases de caracteres.
     */
    private static final Pattern NOMBRE_DE_HOST = Pattern.compile(
            "^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);

    private final String timeoutMillis;

    public DnsResolverMxAdapter(@Value("${renaser.email.dns-timeout-ms:3000}") String timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public ResultadoMx consultar(String dominio) {
        if (dominio == null || !NOMBRE_DE_HOST.matcher(dominio).matches()) {
            // No es un host: nunca llega al interprete JNDI. Es la misma respuesta que da un
            // dominio que no existe, porque para el formulario significa lo mismo.
            return ResultadoMx.DOMINIO_INEXISTENTE;
        }
        DirContext contexto = null;
        try {
            contexto = abrirContexto(configuracion());
            Attributes atributos = contexto.getAttributes(dominio, SOLO_MX);
            Attribute mx = atributos.get("MX");
            // Un MX ausente y uno vacio son lo mismo: nadie puede entregar ahi.
            return mx == null || mx.size() == 0 ? ResultadoMx.SIN_MX : ResultadoMx.TIENE_MX;
        } catch (NameNotFoundException e) {
            // NXDOMAIN: respuesta firme del DNS, no un fallo nuestro.
            return ResultadoMx.DOMINIO_INEXISTENTE;
        } catch (NamingException e) {
            // Timeout, SERVFAIL, sin red. No se sabe; nunca se convierte en un "no".
            log.warn("[users.DnsResolverMxAdapter] no se pudo resolver MX (causa {})",
                    e.getClass().getSimpleName());
            return ResultadoMx.INDETERMINADO;
        } finally {
            cerrar(contexto);
        }
    }

    /**
     * Unico punto donde se construye el contexto JNDI. Es visible al paquete — y no privado —
     * para que la prueba pueda contar cuantas veces se construye uno, y con que entorno, sin
     * salir a la red: la garantia que hay que sostener es que un nombre con forma de URL no
     * llega nunca hasta aca. No es un punto de extension.
     */
    DirContext abrirContexto(Hashtable<String, String> entorno) throws NamingException {
        return new InitialDirContext(entorno);
    }

    private Hashtable<String, String> configuracion() {
        Hashtable<String, String> entorno = new Hashtable<>();
        entorno.put(DirContext.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        entorno.put(DirContext.PROVIDER_URL, "dns:");
        entorno.put("com.sun.jndi.dns.timeout.initial", timeoutMillis);
        entorno.put("com.sun.jndi.dns.timeout.retries", "1");
        // Cinturon y tirantes: estas dos claves NO evitan la conexion saliente —de eso se ocupa
        // NOMBRE_DE_HOST—, solo le ponen plazo. Valen por si la reja de arriba se aflojara algun
        // dia: sin ellas LdapCtx deja connectTimeout y readTimeout en -1, y entonces esperar la
        // respuesta es un take() sin plazo contra el socket del otro lado y la request no
        // termina nunca. El proveedor las lee con Integer.parseInt, igual que las de DNS.
        entorno.put("com.sun.jndi.ldap.connect.timeout", timeoutMillis);
        entorno.put("com.sun.jndi.ldap.read.timeout", timeoutMillis);
        return entorno;
    }

    private void cerrar(DirContext contexto) {
        if (contexto == null) {
            return;
        }
        try {
            contexto.close();
        } catch (NamingException e) {
            log.warn("[users.DnsResolverMxAdapter] no se pudo cerrar el contexto DNS (causa {})",
                    e.getClass().getSimpleName());
        }
    }
}
