package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un conjunto FIJO de filas de la Caja para las pruebas del CSV (spec 050, F; SC-008): de todo lo que el CSV tiene que
 * escapar o dejar vacío. Sirve igual para la prueba de caracterización (el archivo dorado, capturado con el código de
 * antes de pasar a streaming) y para la del streaming, que tiene que dar lo mismo.
 */
final class FilasDeCajaParaLasPruebas {

    private FilasDeCajaParaLasPruebas() {
    }

    /** Las filas de siempre: sin nada, con envío, con fórmulas, con comillas, punto y coma y saltos de línea. */
    static List<ResumenDeCaja> deSiempre() {
        List<ResumenDeCaja> filas = new ArrayList<>();
        filas.add(fila(1, "Ana Pérez", "Grupo de Ana", 10, EstadoCaja.POR_REVISAR, 0, null, new BigDecimal("1.00"),
                ficha("Ana Pérez", "999111222", "Perú", "Lima", "Miraflores", "Av. Larco 123", "45678912",
                        new DestinoAlternativo(null, null, null, "Frente al parque", "Lima")), null));
        filas.add(fila(2, "Beto Díaz", null, 10, EstadoCaja.EN_EVALUACION, 0, null, null,
                ficha(null, null, null, null, null, null, null, null), null));
        filas.add(fila(3, "Carla; Soto \"la Chilena\"", "Grupo B", 25, EstadoCaja.ENVIADA, 1,
                Instant.parse("2026-11-03T14:30:00Z"), new BigDecimal("0.85"),
                ficha("Carla Soto", "+56 9 1111 2222", "Chile", "Santiago", "Providencia", "Calle 1; piso 2\nreferencia",
                        "X123", new DestinoAlternativo("Otra calle 5", "988777666", "Su mamá", "Casa verde", "Santiago")),
                new DatosDelEnvio("Olva", "Olva Courier", "OL-0001", new BigDecimal("12.50"))));
        filas.add(fila(4, "=HYPERLINK(\"http://x\")", "+grupo", 30, EstadoCaja.ENTREGADA, 2,
                Instant.parse("2026-11-04T01:00:00Z"), new BigDecimal("1.00"),
                ficha("-cmd", "@celular", "Perú", "=Lima", "+Barranco", "@Dirección\r\ncon salto", "=1+1",
                        new DestinoAlternativo("=otra", "-1", "+quien", "@ref", "=prov")),
                new DatosDelEnvio("inDrive", null, "=PLACA", null)));
        filas.add(fila(5, "Ñandú Ávila", "Grupo ñ", 12, EstadoCaja.CON_PROBLEMA, 1, Instant.parse("2026-11-05T05:00:00Z"),
                null, ficha("Ñandú", "", "PERU", "Cusco", "", "Jr. Sol 4", "", DestinoAlternativo.VACIO),
                new DatosDelEnvio("Shalom", "Shalom", "SH-9", new BigDecimal("0"))));
        filas.add(fila(6, "Diana Rojas", "Grupo A", 40, EstadoCaja.ARMANDO, 0, null, new BigDecimal("0.50"),
                ficha("Diana", "900000000", "Perú", "Arequipa", "Cayma", "Calle \"Sol\" 7", "11112222",
                        DestinoAlternativo.VACIO), null));
        filas.add(fila(7, "Elena Vega", null, 9, EstadoCaja.EN_PAUSA, 0, null, null,
                ficha("Elena", null, "Perú", null, null, null, null, DestinoAlternativo.VACIO), null));
        filas.add(fila(8, "Fabiola Ríos", "Grupo C", 8, EstadoCaja.FUERA_DE_LA_APP, 0, null, null,
                ficha("Fabiola", "5551234", "México", "CDMX", "Coyoacán", "Av. 5", "ABC", DestinoAlternativo.VACIO), null));
        return filas;
    }

    /** {@code n} filas distintas (para probar muchos lotes). El orden de las filas es el de esta lista. */
    static List<ResumenDeCaja> varias(int n) {
        List<ResumenDeCaja> filas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            filas.add(fila(1000 + i, "Persona " + String.format("%05d", i), i % 7 == 0 ? null : "Grupo " + (i % 5), 8 + i % 80,
                    EstadoCaja.values()[1 + i % (EstadoCaja.values().length - 1)], i % 3, null, null,
                    ficha("Persona " + i, "9" + String.format("%08d", i), "Perú", "Lima", "Distrito " + i, "Calle " + i, "D" + i,
                            DestinoAlternativo.VACIO), null));
        }
        return filas;
    }

    static ResumenDeCaja fila(int numero, String nombre, String grupo, int dia, EstadoCaja estado, int envio,
                              Instant actualizadoEn, BigDecimal cumplimiento, FichaDeEnvio ficha, DatosDelEnvio datos) {
        return new ResumenDeCaja(UserId.of(new UUID(0L, numero)), nombre, grupo, dia, estado, envio, actualizadoEn,
                cumplimiento, ficha, datos);
    }

    static FichaDeEnvio ficha(String nombre, String celular, String pais, String ciudad, String distrito, String direccion,
                              String dni, DestinoAlternativo destino) {
        return new FichaDeEnvio(nombre, celular, pais, ciudad, distrito, direccion, dni, destino);
    }
}
