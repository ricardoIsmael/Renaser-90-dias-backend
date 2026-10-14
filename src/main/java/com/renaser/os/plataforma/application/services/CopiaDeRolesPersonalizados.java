package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.roles.FotoDeRolesPersonalizados;
import com.renaser.os.plataforma.domain.model.roles.RolEnMemoria;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * La copia en memoria de los roles personalizados de ESTA instancia (contrato de la fase 2, §4.4; D-269): persona → rol
 * y rol → claves. La regla de disponibilidad la lee en cada pedido sin tocar la base.
 *
 * <p>Se recarga entera al arrancar, después de cada {@code RolPersonalizadoCambiadoEvent} de esta instancia, cuando
 * otra avisa por el canal Redis {@code renaser:roles-personalizados} y, de respaldo, cada 30 s. Si la base no responde,
 * conserva la última copia; si nunca la pudo leer, nadie tiene rol personalizado y no se restringe nada (la misma
 * postura que «una regla que falla se trata como disponible»).
 */
@Service
public class CopiaDeRolesPersonalizados {

    private static final Logger log = LoggerFactory.getLogger(CopiaDeRolesPersonalizados.class);

    private final RolesPersonalizadosFinder finder;
    private final AtomicReference<FotoDeRolesPersonalizados> foto =
            new AtomicReference<>(FotoDeRolesPersonalizados.vacia());

    public CopiaDeRolesPersonalizados(RolesPersonalizadosFinder finder) {
        this.finder = finder;
    }

    public FotoDeRolesPersonalizados foto() {
        return foto.get();
    }

    /** Relee la base y reemplaza la copia entera. Un fallo no la vacía: se sigue con la anterior. */
    public synchronized void recargar() {
        try {
            Map<UUID, RolEnMemoria> activos = new HashMap<>();
            for (RolPersonalizadoResumen rol : finder.todos()) {
                if (rol.activo()) {
                    activos.put(rol.id(), new RolEnMemoria(rol.rolBase(), rol.funcionalidades()));
                }
            }
            foto.set(new FotoDeRolesPersonalizados(finder.asignaciones(), activos));
        } catch (RuntimeException e) {
            log.warn("[plataforma] no se pudieron recargar los roles personalizados; sigue la copia anterior ({} "
                    + "personas con rol)", foto.get().personaARol().size(), e);
        }
    }
}
