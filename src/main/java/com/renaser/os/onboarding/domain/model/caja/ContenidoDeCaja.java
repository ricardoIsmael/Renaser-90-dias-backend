package com.renaser.os.onboarding.domain.model.caja;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La lista vigente de lo que lleva la caja (editable por el Admin) y las reglas del checklist.
 */
public record ContenidoDeCaja(List<ElementoDeCaja> elementos) {

    public static final int MAXIMO = 30;

    public ContenidoDeCaja {
        elementos = List.copyOf(elementos);
    }

    /** Una lista nueva. @throws IllegalArgumentException si está vacía, es muy larga o repite una clave (400) */
    public static ContenidoDeCaja nueva(List<ElementoDeCaja> elementos) {
        if (elementos == null || elementos.isEmpty()) {
            throw new IllegalArgumentException("La caja tiene que llevar al menos un elemento.");
        }
        if (elementos.size() > MAXIMO) {
            throw new IllegalArgumentException("La caja puede llevar hasta " + MAXIMO + " elementos.");
        }
        Set<String> vistos = new HashSet<>();
        for (ElementoDeCaja elemento : elementos) {
            if (!vistos.add(elemento.valor())) {
                throw new IllegalArgumentException("«" + elemento.etiqueta() + "» está repetido.");
            }
        }
        return new ContenidoDeCaja(elementos);
    }

    /** @throws IllegalArgumentException si se marcó algo que no está en la lista (400) */
    public Set<String> exigirMarcables(List<String> marcados) {
        Set<String> valores = valores();
        Set<String> limpios = new HashSet<>();
        for (String marcado : marcados == null ? List.<String>of() : marcados) {
            if (!valores.contains(marcado)) {
                throw new IllegalArgumentException("«" + marcado + "» no está en el contenido de la caja.");
            }
            limpios.add(marcado);
        }
        return limpios;
    }

    /** Si el checklist tiene marcado TODO lo de la lista vigente. */
    public boolean completo(Set<String> marcados) {
        return marcados.containsAll(valores());
    }

    private Set<String> valores() {
        Set<String> valores = new HashSet<>();
        elementos.forEach(e -> valores.add(e.valor()));
        return valores;
    }
}
