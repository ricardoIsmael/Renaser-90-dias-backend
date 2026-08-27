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

/**
 * Consulta MX por DNS con el proveedor JNDI que ya trae el JDK — sin dependencias nuevas. Es el
 * equivalente del {@code dns.resolveMx} de Node que usaba el repo viejo.
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

    private final String timeoutMillis;

    public DnsResolverMxAdapter(@Value("${renaser.email.dns-timeout-ms:3000}") String timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public ResultadoMx consultar(String dominio) {
        DirContext contexto = null;
        try {
            contexto = new InitialDirContext(configuracion());
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

    private Hashtable<String, String> configuracion() {
        Hashtable<String, String> entorno = new Hashtable<>();
        entorno.put(DirContext.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        entorno.put(DirContext.PROVIDER_URL, "dns:");
        entorno.put("com.sun.jndi.dns.timeout.initial", timeoutMillis);
        entorno.put("com.sun.jndi.dns.timeout.retries", "1");
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
