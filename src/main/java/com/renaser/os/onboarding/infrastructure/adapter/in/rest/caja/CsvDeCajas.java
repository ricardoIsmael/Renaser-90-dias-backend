package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase.ExportacionDeCajas;
import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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
 *
 * <p>Se escribe en streaming, por lotes (spec 050, tramo F): ningún lote espera al siguiente ni se junta con él.
 */
final class CsvDeCajas {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final List<String> COLUMNAS = List.of("aprendizId", "nombre", "grupo", "diaPrograma", "estado",
            "envio", "cumplimientoFase1", "nombreEnvio", "celular", "dni", "pais", "provincia", "ciudad", "distrito",
            "direccion", "referencias", "quienRecibe", "otraDireccion", "otroCelular", "medio", "courier", "codigo",
            "costo", "actualizadoEn");

    private CsvDeCajas() {
    }

    /**
     * Escribe la descarga <b>por lotes</b>: el BOM y la cabecera salen primero, y cada lote que entrega la exportación se
     * convierte en filas, se escribe y se suelta (spec 050, tramo F; FR-032). La memoria es la de un lote, no la del padrón;
     * el contenido, las columnas, el orden y el escape son los de siempre (la prueba lo compara byte a byte).
     */
    static void escribir(ExportacionDeCajas exportacion, OutputStream salida) throws IOException {
        salida.write(BOM);
        salida.write((String.join(";", COLUMNAS) + "\r\n").getBytes(StandardCharsets.UTF_8));
        salida.flush();
        try {
            exportacion.porLotes(lote -> escribirLote(lote, salida));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void escribirLote(List<ResumenDeCaja> lote, OutputStream salida) {
        StringBuilder csv = new StringBuilder();
        lote.forEach(caja -> csv.append(fila(caja).stream().map(CsvDeCajas::celda)
                .collect(Collectors.joining(";"))).append("\r\n"));
        try {
            salida.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            salida.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
