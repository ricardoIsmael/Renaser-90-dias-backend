package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La copia de los despliegues en curso que cada instancia tiene en memoria (contrato fase 2 §3.2). Inmutable: una
 * recarga arma otra y la reemplaza entera. Consultarla es leer un mapa, nunca la base.
 */
public final class FotoDeDespliegues {

    private static final FotoDeDespliegues VACIA = new FotoDeDespliegues(Map.of());

    private final Map<String, DespliegueDeFuncionalidad> porClave;

    private FotoDeDespliegues(Map<String, DespliegueDeFuncionalidad> porClave) {
        this.porClave = porClave;
    }

    /** Antes de leer la base, o si nunca se pudo: nada en despliegue, todo llega a todos. */
    public static FotoDeDespliegues vacia() {
        return VACIA;
    }

    public static FotoDeDespliegues de(Collection<DespliegueDeFuncionalidad> despliegues) {
        return new FotoDeDespliegues(despliegues.stream()
                .collect(Collectors.toUnmodifiableMap(DespliegueDeFuncionalidad::clave, Function.identity())));
    }

    /** Sin nada en despliegue: el camino de casi todos los pedidos, que no tiene que hacer nada más. */
    public boolean sinDespliegues() {
        return porClave.isEmpty();
    }

    public Optional<DespliegueDeFuncionalidad> de(String clave) {
        return Optional.ofNullable(porClave.get(clave));
    }

    /**
     * El primer nivel de la cadena (de la raíz a la clave pedida) que está en despliegue y deja fuera a esta persona:
     * una madre en despliegue esconde a todas sus hijas, aunque la hija no tenga despliegue propio.
     *
     * @return la clave de ese nivel, o vacío si le llegan todos los niveles
     */
    public Optional<String> fueraEn(List<String> cadena, UserId persona) {
        if (porClave.isEmpty()) {
            return Optional.empty();
        }
        for (String nivel : cadena) {
            DespliegueDeFuncionalidad despliegue = porClave.get(nivel);
            if (despliegue != null && !despliegue.incluye(persona)) {
                return Optional.of(nivel);
            }
        }
        return Optional.empty();
    }

    public int cantidad() {
        return porClave.size();
    }
}
