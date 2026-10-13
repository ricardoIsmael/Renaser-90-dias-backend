package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.MisFuncionalidades;

import java.util.List;
import java.util.zip.CRC32;
import java.nio.charset.StandardCharsets;

/** El cuerpo de {@code GET /funcionalidades/mias} (contrato §6). */
record MisFuncionalidadesResponse(long version, List<Apagada> apagadas) {

    static MisFuncionalidadesResponse from(MisFuncionalidades mias) {
        return new MisFuncionalidadesResponse(mias.version(),
                mias.apagadas().stream().map(a -> new Apagada(a.clave(), a.aviso())).toList());
    }

    /**
     * {@code "v<version>-<huella>"}. La huella es la del contenido y no solo la versión, a propósito (desvío mínimo
     * del contrato, que dice {@code "v<version>"}): la respuesta depende del rol de quien pide y de los apagados
     * automáticos de la IA, y ninguno de los dos cambia la versión. Con la versión sola, una persona que cambió de rol
     * o un circuito que se abrió devolverían 304 con un estado viejo. Para la app es opaco: lo guarda y lo reenvía.
     */
    String etag() {
        CRC32 huella = new CRC32();
        apagadas.forEach(a -> huella.update((a.clave() + "=" + a.aviso() + ";").getBytes(StandardCharsets.UTF_8)));
        return "\"v" + version + "-" + Long.toHexString(huella.getValue()) + "\"";
    }

    record Apagada(String clave, String aviso) {
    }
}
