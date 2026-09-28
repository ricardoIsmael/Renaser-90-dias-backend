package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La descarga de la lista (spec §7: «Descargar», con todos los datos de envío para no llamar a nadie).
 *
 * <p>UTF-8 con BOM y {@code ;} de separador: es lo que Excel en español abre bien con doble clic (con
 * {@code ,} lo mete todo en una columna, y sin BOM rompe las tildes). Un valor que empieza con {@code =},
 * {@code +}, {@code -} o {@code @} se escapa con un apóstrofo: la dirección la escribe el aprendiz, y en una
 * planilla una fórmula se ejecuta (inyección CSV).
 */
final class CsvDeCajas {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final List<String> COLUMNAS = List.of("aprendizId", "nombre", "grupo", "diaPrograma", "estado",
            "envio", "cumplimientoFase1", "nombreEnvio", "celular", "dni", "pais", "provincia", "ciudad", "distrito",
            "direccion", "referencias", "quienRecibe", "otraDireccion", "otroCelular", "medio", "courier", "codigo",
            "costo", "actualizadoEn");

    private CsvDeCajas() {
    }

    static byte[] escribir(List<ResumenDeCaja> cajas) {
        StringBuilder csv = new StringBuilder(String.join(";", COLUMNAS)).append("\r\n");
        cajas.forEach(caja -> csv.append(fila(caja).stream().map(CsvDeCajas::celda)
                .collect(Collectors.joining(";"))).append("\r\n"));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(BOM);
        salida.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return salida.toByteArray();
    }

    private static List<Object> fila(ResumenDeCaja c) {
        FichaDeEnvio f = c.ficha();
        DestinoAlternativo d = f.destino();
        DatosDelEnvio e = c.envioDatos();
        List<Object> fila = new ArrayList<>(List.of(c.aprendizId().value(), texto(c.nombre()), texto(c.grupo()),
                c.diaPrograma(), c.estado(), c.envio(), texto(c.cumplimientoFase1())));
        fila.addAll(List.of(texto(f.nombre()), texto(f.celular()), texto(f.dni()), texto(f.pais()),
                texto(d.provincia()), texto(f.ciudad()), texto(f.distrito()), texto(f.direccion()),
                texto(d.referencias()), texto(d.quienRecibe()), texto(d.otraDireccion()), texto(d.otroCelular())));
        fila.addAll(List.of(texto(e == null ? null : e.medio()), texto(e == null ? null : e.courier()),
                texto(e == null ? null : e.codigo()), texto(e == null || e.costo() == null ? null : e.costo()),
                texto(c.actualizadoEn())));
        return fila;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String celda(Object valor) {
        String texto = valor.toString();
        if (!texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
