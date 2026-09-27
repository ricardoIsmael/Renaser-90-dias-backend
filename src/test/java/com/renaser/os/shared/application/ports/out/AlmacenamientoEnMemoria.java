package com.renaser.os.shared.application.ports.out;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Un almacenamiento de verdad, pero en memoria, para las pruebas que tienen que subir y volver a leer un
 * objeto (la portada de la bienvenida, D-210). Anota cada lectura para poder decir cuántas veces se bajó.
 */
public final class AlmacenamientoEnMemoria implements AlmacenamientoPort {

    private final Map<String, byte[]> objetos = new HashMap<>();
    private final List<String> leidas = new ArrayList<>();

    public void guardar(String ruta, byte[] contenido) {
        objetos.put(ruta, contenido);
    }

    public List<String> leidas() {
        return List.copyOf(leidas);
    }

    @Override
    public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
        return URI.create("https://almacen.test/" + ruta + "?subida");
    }

    @Override
    public URI firmarLectura(String ruta, Duration validez) {
        return URI.create("https://almacen.test/" + ruta + "?lectura");
    }

    @Override
    public URI urlPublica(String ruta) {
        return URI.create("https://almacen.test/" + ruta);
    }

    @Override
    public void subir(String ruta, byte[] contenido, String tipoContenido) {
        objetos.put(ruta, contenido);
    }

    @Override
    public void borrar(String ruta) {
        objetos.remove(ruta);
    }

    @Override
    public Optional<byte[]> leer(String ruta, long pesoMaximo) {
        leidas.add(ruta);
        byte[] contenido = objetos.get(ruta);
        if (contenido != null && contenido.length > pesoMaximo) {
            throw new IllegalArgumentException("El archivo pesa más de " + pesoMaximo + " bytes");
        }
        return Optional.ofNullable(contenido);
    }
}
