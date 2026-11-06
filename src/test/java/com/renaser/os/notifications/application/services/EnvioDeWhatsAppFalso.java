package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.whatsapp.EnvioDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** El proveedor de WhatsApp de mentira (spec 041): cuenta las llamadas y devuelve lo que se le diga. Las pruebas nunca llaman a Meta ni a YCloud. */
final class EnvioDeWhatsAppFalso implements EnvioDeWhatsAppPort {

    record Llamada(String destino, UUID externalId, List<String> huecos, String cargaDelBoton) {
    }

    boolean configurado = true;
    Supplier<ResultadoDelEnvio> respuesta = () -> ResultadoDelEnvio.aceptado("ycloud-1", "wamid.1");
    final List<Llamada> llamadas = new ArrayList<>();
    final List<String[]> textos = new ArrayList<>();

    @Override
    public boolean configurado() {
        return configurado;
    }

    @Override
    public ResultadoDelEnvio enviarRecordatorio(String destino, UUID externalId, List<String> huecos, String cargaDelBoton) {
        llamadas.add(new Llamada(destino, externalId, List.copyOf(huecos), cargaDelBoton));
        return respuesta.get();
    }

    final List<String[]> codigos = new ArrayList<>();

    @Override
    public boolean configuradoParaCodigos() {
        return configurado;
    }

    @Override
    public ResultadoDelEnvio enviarCodigo(String destino, String codigo) {
        codigos.add(new String[] {destino, codigo});
        return respuesta.get();
    }

    @Override
    public void enviarTexto(String destino, String texto) {
        textos.add(new String[] {destino, texto});
    }
}
