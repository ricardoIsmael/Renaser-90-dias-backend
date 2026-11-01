package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.AvisoDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.ErrorDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.PreguntaImportada;
import com.renaser.os.shared.domain.Formacion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.ALIMENTA;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.AYUDA;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.CLAVE;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.COLUMNAS;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.CONDICION;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.FORMACIONES;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.MAXIMO;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.NOMBRE_DEL_TIPO;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.OBLIGATORIA;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.OPCIONES;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.PREGUNTA;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.SECCION;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.TIPO;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.plano;
import static com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.sinPuntuacion;

/** Lee las filas de la hoja ya separada en celdas: valida cada una con las reglas del dominio y junta los errores por número de fila (la cabecera es la 1). */
// ---------------------------------------------------------------------------------------------- la lectura

final class LectorDeFilas {
    private final int[] columnas;
    private final List<List<String>> registros;
    private final Map<String, List<OpcionDelDiagnostico>> existentes;
    private final List<ErrorDeImportacion> errores;
    private final List<AvisoDeImportacion> avisos;
    private final List<PreguntaImportada> preguntas = new ArrayList<>();
    private final Map<String, Integer> filaDeLaClave = new HashMap<>();
    private final Map<String, PreguntaImportada> porClave = new LinkedHashMap<>();
    private final Map<DestinoDeRespuesta, Integer> filaDelDestino = new EnumMap<>(DestinoDeRespuesta.class);
    private final Set<String> vistas = new HashSet<>();

    LectorDeFilas(int[] columnas, List<List<String>> registros, Map<String, List<OpcionDelDiagnostico>> existentes,
            List<ErrorDeImportacion> errores, List<AvisoDeImportacion> avisos) {
        this.columnas = columnas;
        this.registros = registros;
        this.existentes = existentes;
        this.errores = errores;
        this.avisos = avisos;
    }

    LecturaDeImportacion resolver() {
        for (int i = 1; i < registros.size(); i++) {
            List<String> fila = registros.get(i);
            if (fila.stream().allMatch(c -> c.isBlank())) {
                continue;
            }
            leerFila(i + 1, fila);
        }
        if (preguntas.isEmpty() && errores.isEmpty()) {
            errores.add(new ErrorDeImportacion(1, null, "El archivo no trae ninguna pregunta"));
        }
        return new LecturaDeImportacion(preguntas, errores, avisos);
    }

    private String celda(List<String> fila, int columna) {
        int i = columnas[columna];
        return i < 0 || i >= fila.size() ? "" : fila.get(i).trim();
    }

    private void leerFila(int numero, List<String> fila) {
        int erroresAntes = errores.size();
        String seccion = celda(fila, SECCION);
        if (seccion.isEmpty()) {
            error(numero, SECCION, "Falta la sección");
        }
        Optional<TipoPreguntaOnboarding> tipo = tipo(numero, celda(fila, TIPO));
        Optional<DestinoDeRespuesta> alimenta = alimenta(numero, celda(fila, ALIMENTA));
        boolean requerida = obligatoria(numero, celda(fila, OBLIGATORIA));
        Set<Formacion> formaciones = formaciones(numero, celda(fila, FORMACIONES));
        if (tipo.isEmpty() || errores.size() > erroresAntes) {
            return;
        }
        String clave = celda(fila, CLAVE);
        if (!clave.isEmpty() && filaDeLaClave.putIfAbsent(clave, numero) != null) {
            error(numero, CLAVE, "La clave «" + clave + "» ya la usa la fila " + filaDeLaClave.get(clave));
            return;
        }
        Optional<PreguntaDelDiagnostico> pregunta = construir(numero, fila, tipo.get(), requerida, alimenta.orElse(null), formaciones);
        if (pregunta.isEmpty()) {
            return;
        }
        String[] condicion = condicion(numero, celda(fila, CONDICION));
        if (errores.size() > erroresAntes) {
            return;
        }
        alimenta.ifPresent(d -> destinoUnico(numero, d));
        avisoDeDuplicada(numero, seccion, pregunta.get().texto());
        PreguntaImportada importada = new PreguntaImportada(numero, seccion, clave.isEmpty() ? null : clave, pregunta.get(),
                condicion == null ? null : condicion[0], condicion == null ? null : condicion[1]);
        preguntas.add(importada);
        if (!clave.isEmpty()) {
            porClave.put(clave, importada);
        }
    }

    private Optional<PreguntaDelDiagnostico> construir(int numero, List<String> fila, TipoPreguntaOnboarding tipo, boolean requerida,
                                                      DestinoDeRespuesta alimenta, Set<Formacion> formaciones) {
        try {
            List<OpcionDelDiagnostico> opciones = OpcionesDeImportacion.opciones(tipo, alimenta, celda(fila, OPCIONES));
            EscalaDelDiagnostico escala = tipo == TipoPreguntaOnboarding.ESCALA ? OpcionesDeImportacion.escala(celda(fila, OPCIONES)) : null;
            if (tipo == TipoPreguntaOnboarding.ESCALA) {
                opciones = List.of();
            }
            Integer maximo = maximo(numero, celda(fila, MAXIMO));
            String precarga = null;
            return Optional.of(PreguntaDelDiagnostico.nueva(0, 0, celda(fila, CLAVE), celda(fila, PREGUNTA),
                    celda(fila, AYUDA).replace(" · ", "\n"), tipo, requerida, maximo, opciones, escala, null, alimenta, formaciones,
                    precarga));
        } catch (IllegalArgumentException e) {
            error(numero, null, e.getMessage());
            return Optional.empty();
        }
    }

    private Integer maximo(int numero, String celda) {
        if (celda.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(celda);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El máximo de opciones tiene que ser un número");
        }
    }

    private Optional<TipoPreguntaOnboarding> tipo(int numero, String celda) {
        if (celda.isEmpty()) {
            error(numero, TIPO, "Falta el tipo");
            return Optional.empty();
        }
        String buscado = plano(celda);
        Optional<TipoPreguntaOnboarding> encontrado = NOMBRE_DEL_TIPO.entrySet().stream()
                .filter(e -> plano(e.getValue()).equals(buscado)).map(Map.Entry::getKey).findFirst();
        if (encontrado.isEmpty()) {
            error(numero, TIPO, "El tipo «" + celda + "» no existe" + sugerencia(buscado) + ". Los tipos son: "
                    + String.join(", ", NOMBRE_DEL_TIPO.values()));
        }
        return encontrado;
    }

    private static String sugerencia(String buscado) {
        return NOMBRE_DEL_TIPO.values().stream().filter(n -> {
            String p = plano(n);
            return p.startsWith(buscado) || buscado.startsWith(p) || p.contains(buscado) && buscado.length() >= 4;
        }).findFirst().map(n -> "; usa «" + n + "»").orElse("");
    }

    private Optional<DestinoDeRespuesta> alimenta(int numero, String celda) {
        if (celda.isEmpty() || plano(celda).equals("nada")) {
            return Optional.empty();
        }
        Optional<DestinoDeRespuesta> destino = DestinoDeRespuesta.desdeEtiqueta(celda);
        if (destino.isEmpty()) {
            error(numero, ALIMENTA, "«Alimenta» no puede ser «" + celda + "». Opciones: "
                    + String.join(", ", Arrays.stream(DestinoDeRespuesta.values()).map(DestinoDeRespuesta::etiqueta).toList()));
        }
        return destino;
    }

    private boolean obligatoria(int numero, String celda) {
        if (celda.isEmpty()) {
            return false;
        }
        String p = plano(celda);
        if (p.equals("si") || p.equals("true") || p.equals("1")) {
            return true;
        }
        if (!(p.equals("no") || p.equals("false") || p.equals("0"))) {
            error(numero, OBLIGATORIA, "«Obligatoria» se escribe Sí o No, no «" + celda + "»");
        }
        return false;
    }

    private Set<Formacion> formaciones(int numero, String celda) {
        Set<Formacion> formaciones = new LinkedHashSet<>();
        for (String nombre : celda.split("\\|")) {
            if (nombre.isBlank()) {
                continue;
            }
            String p = plano(nombre);
            Optional<Formacion> f = Arrays.stream(Formacion.values())
                    .filter(c -> plano(c.codigo()).equals(p) || p.startsWith(plano(c.codigo()).substring(0, 5))).findFirst();
            if (f.isEmpty()) {
                error(numero, FORMACIONES, "La formación «" + nombre.trim() + "» no existe: usa Escuela, Diplomado o Certificación");
            } else {
                formaciones.add(f.get());
            }
        }
        return formaciones;
    }

    private String[] condicion(int numero, String celda) {
        if (celda.isEmpty()) {
            return null;
        }
        int igual = celda.indexOf('=');
        if (igual <= 0 || igual == celda.length() - 1) {
            error(numero, CONDICION, "«Se muestra solo si» se escribe como «clave=Opción», por ejemplo p13=Sí.");
            return null;
        }
        String clave = celda.substring(0, igual).trim();
        String valor = celda.substring(igual + 1).trim();
        PreguntaImportada delArchivo = porClave.get(clave);
        List<OpcionDelDiagnostico> opciones = delArchivo != null ? delArchivo.pregunta().opciones() : existentes.get(clave);
        if (opciones == null) {
            String motivo = filaDeLaClave.containsKey(clave) ? "está después de esta fila" : "no existe";
            error(numero, CONDICION, "«Se muestra solo si» apunta a la clave «" + clave + "», que " + motivo
                    + ". Tiene que ser una pregunta anterior");
            return null;
        }
        Optional<OpcionDelDiagnostico> opcion = opciones.stream().filter(o -> sinPuntuacion(o.valor()).equals(sinPuntuacion(valor))).findFirst();
        if (opcion.isEmpty()) {
            error(numero, CONDICION, "«" + valor + "» no es una opción de la pregunta «" + clave + "»");
            return null;
        }
        return new String[]{clave, opcion.get().valor()};
    }

    private void destinoUnico(int numero, DestinoDeRespuesta destino) {
        Integer previa = filaDelDestino.putIfAbsent(destino, numero);
        if (previa != null) {
            error(numero, ALIMENTA, "«" + destino.etiqueta() + "» ya lo alimenta la fila " + previa + ": cada destino admite una sola pregunta");
        }
    }

    private void avisoDeDuplicada(int numero, String seccion, String texto) {
        if (!vistas.add(plano(seccion) + "|" + plano(texto))) {
            avisos.add(new AvisoDeImportacion(numero, "La pregunta «" + texto + "» está repetida en la sección «" + seccion + "»"));
        }
    }

    private void error(int numero, int columna, String mensaje) {
        errores.add(new ErrorDeImportacion(numero, COLUMNAS.get(columna), mensaje));
    }

    private void error(int numero, String columna, String mensaje) {
        errores.add(new ErrorDeImportacion(numero, columna, mensaje));
    }
}
