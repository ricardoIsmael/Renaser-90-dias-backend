package com.renaser.os.plataforma.domain.model.catalogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El catálogo de funcionalidades, declarado en código (spec 002, FR-001; plan.md D4). La base guarda solo los desvíos
 * de su valor por defecto. Las funcionalidades anti-bloqueo (login, sesión, lectura de interruptores, consola, health)
 * no están acá: no tienen interruptor (FR-004).
 */
public final class CatalogoDeFuncionalidades {

    private static final CatalogoDeFuncionalidades INICIAL = de(EntradasDelCatalogo.iniciales());

    private final Map<String, DefinicionDeFuncionalidad> porClave;

    private CatalogoDeFuncionalidades(Map<String, DefinicionDeFuncionalidad> porClave) {
        this.porClave = porClave;
    }

    /** El catálogo de la spec 002. */
    public static CatalogoDeFuncionalidades inicial() {
        return INICIAL;
    }

    /** Un catálogo a medida (pruebas). Rechaza claves repetidas y una hija sin su madre. */
    public static CatalogoDeFuncionalidades de(List<DefinicionDeFuncionalidad> definiciones) {
        Map<String, DefinicionDeFuncionalidad> mapa = new LinkedHashMap<>();
        for (DefinicionDeFuncionalidad definicion : definiciones) {
            if (mapa.putIfAbsent(definicion.clave(), definicion) != null) {
                throw new IllegalArgumentException("Clave repetida en el catalogo: " + definicion.clave());
            }
        }
        for (DefinicionDeFuncionalidad definicion : mapa.values()) {
            if (definicion.madre() != null && !mapa.containsKey(definicion.madre())) {
                throw new IllegalArgumentException("La madre de " + definicion.clave() + " no esta en el catalogo");
            }
        }
        return new CatalogoDeFuncionalidades(Collections.unmodifiableMap(mapa));
    }

    public Optional<DefinicionDeFuncionalidad> definicion(String clave) {
        return Optional.ofNullable(clave == null ? null : porClave.get(clave));
    }

    public boolean existe(String clave) {
        return clave != null && porClave.containsKey(clave);
    }

    /** Todas, en el orden de la tabla. */
    public List<DefinicionDeFuncionalidad> todas() {
        return List.copyOf(porClave.values());
    }

    /** De la raíz a la clave: {@code [chat, chat.grupos]}. Vacía si la clave no existe. */
    public List<DefinicionDeFuncionalidad> cadena(String clave) {
        List<DefinicionDeFuncionalidad> cadena = new ArrayList<>();
        String actual = clave;
        while (actual != null && porClave.containsKey(actual)) {
            DefinicionDeFuncionalidad definicion = porClave.get(actual);
            cadena.addFirst(definicion);
            actual = definicion.madre();
        }
        return cadena;
    }
}
