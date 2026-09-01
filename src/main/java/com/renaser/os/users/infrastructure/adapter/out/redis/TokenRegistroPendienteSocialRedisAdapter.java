package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Clave {@code registro-pendiente-social:{tokenOpaco}} -> valor: los 4 campos de
 * {@link RegistroPendienteSocial} en un solo string (docs/MODULO_AUTH.md §6.10). TTL nativo de
 * Redis, sin cron de purga — mismo patron exacto que {@link TokenResetContrasenaRedisAdapter}.
 *
 * <p>Se codifican con un separador de control (U+0001, imposible de tipear e imposible que
 * aparezca en un ID token) en vez de JSON: ningun otro adaptador de este paquete serializa un
 * objeto compuesto todavia, y sumar Jackson aca por un solo caso no se justifica. El separador
 * no puede aparecer por accidente en un nombre de enum, en un {@code sub} opaco de OAuth, ni en
 * texto que una persona tipea en un formulario.
 */
@Component
class TokenRegistroPendienteSocialRedisAdapter implements TokenRegistroPendienteSocialPort {

    private static final String CLAVE_PREFIJO = "registro-pendiente-social:";

    /** 256 bits de entropia — mismo criterio que el resto de los tokens de este paquete. */
    private static final int BYTES_ALEATORIOS = 32;

    private static final String SEPARADOR = "";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    TokenRegistroPendienteSocialRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generar(RegistroPendienteSocial registro, Duration vigencia) {
        String token = tokenAleatorio();
        redisTemplate.opsForValue().set(clave(token), serializar(registro), vigencia);
        return token;
    }

    /**
     * {@code getAndDelete} emite {@code GETDEL}: lectura y borrado en un solo viaje atomico a
     * Redis, mismo motivo que {@link TokenResetContrasenaRedisAdapter#consumir}: el "un solo
     * uso" no puede depender de un GET seguido de un DEL desde este lado, o dos requests casi
     * simultaneas con el mismo token podrian las dos leer "exito" antes de que cualquiera borre
     * la clave.
     */
    @Override
    public Optional<RegistroPendienteSocial> consumir(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String crudo = redisTemplate.opsForValue().getAndDelete(clave(token));
        return Optional.ofNullable(crudo).map(TokenRegistroPendienteSocialRedisAdapter::deserializar);
    }

    private static String serializar(RegistroPendienteSocial registro) {
        return registro.proveedor().name() + SEPARADOR + registro.sujetoProveedor() + SEPARADOR
                + registro.email() + SEPARADOR + registro.fullName();
    }

    private static RegistroPendienteSocial deserializar(String crudo) {
        String[] partes = crudo.split(SEPARADOR, -1);
        return new RegistroPendienteSocial(ProveedorIdentidad.valueOf(partes[0]), partes[1], partes[2], partes[3]);
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String clave(String token) {
        return CLAVE_PREFIJO + token;
    }
}
