package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.plano;

/** Lee la celda «Opciones o filas» de la hoja: las opciones (con su código cuando alimentan algo) y el rango de una escala. */
final class OpcionesDeImportacion {

    private OpcionesDeImportacion() {
    }

    static List<OpcionDelDiagnostico> opciones(TipoPreguntaOnboarding tipo, DestinoDeRespuesta alimenta, String celda) {
        boolean conCodigo = alimenta != null && alimenta.valoresPermitidos().isPresent();
        List<String> partes = Arrays.stream(celda.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (tipo == TipoPreguntaOnboarding.CASILLA && partes.isEmpty()) {
            return List.of(OpcionDelDiagnostico.de("He leído y acepto la declaración de responsabilidad y consentimiento."));
        }
        return partes.stream().map(p -> conCodigo ? conCodigo(p, alimenta) : OpcionDelDiagnostico.de(p)).toList();
    }

    /** «Etiqueta = CÓDIGO»; sin código, se deduce de la etiqueta cuando es obvia («Relaciones.», «Salud y bienestar.», «Femenino»…). */
    private static OpcionDelDiagnostico conCodigo(String parte, DestinoDeRespuesta destino) {
        int igual = parte.lastIndexOf('=');
        if (igual >= 0) {
            return new OpcionDelDiagnostico(parte.substring(igual + 1).trim().toUpperCase(Locale.ROOT), parte.substring(0, igual).trim());
        }
        String codigo = codigoDeducido(destino, plano(parte));
        if (codigo == null) {
            throw new IllegalArgumentException("La opción «" + parte + "» tiene que decir a cuál corresponde: escríbela como «Etiqueta = CÓDIGO» ("
                    + String.join(", ", destino.valoresPermitidos().orElseThrow().stream().sorted().toList()) + ")");
        }
        return new OpcionDelDiagnostico(codigo, parte.trim());
    }

    private static String codigoDeducido(DestinoDeRespuesta destino, String etiqueta) {
        if (destino == DestinoDeRespuesta.PRIORIDAD_PRINCIPAL) {
            if (etiqueta.contains("relacion")) {
                return "RELACIONES";
            }
            if (etiqueta.contains("salud") || etiqueta.contains("cuerpo")) {
                return "CUERPO";
            }
            return etiqueta.contains("negocio") || etiqueta.contains("riqueza") || etiqueta.contains("profesi") || etiqueta.contains("trabajo")
                    ? "TRABAJO" : null;
        }
        if (etiqueta.contains("femenin") || etiqueta.contains("ella")) {
            return "FEMENINO";
        }
        if (etiqueta.contains("masculin") || etiqueta.contains(" el)")) {
            return "MASCULINO";
        }
        return etiqueta.contains("neutro") ? "NEUTRO" : null;
    }

    static EscalaDelDiagnostico escala(String celda) {
        if (celda.isBlank()) {
            return EscalaDelDiagnostico.DEL_1_AL_5;
        }
        String[] partes = celda.split("[-–]");
        try {
            return new EscalaDelDiagnostico(Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim()), null, null);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La escala se escribe como «1-5» (de 1 a 10 como máximo)");
        }
    }
}
