package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.ImportacionConErroresException;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.ErrorDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.PreguntaImportada;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Importar preguntas desde una hoja pegada como texto (spec 026, US6). Todo o nada: con un solo error no se guarda nada. Lo importado queda en el borrador.
 */
@Service
public class ImportarDiagnosticoService implements ImportarDiagnosticoUseCase {

    private static final String PREFIJO_DE_LA_CLAVE = "diag_";

    private final GuardiaDelDiagnostico guardia;
    private final BorradorDelDiagnosticoPort borrador;
    private final QuitarPreguntasDelDiagnostico quitar;

    public ImportarDiagnosticoService(GuardiaDelDiagnostico guardia, BorradorDelDiagnosticoPort borrador, QuitarPreguntasDelDiagnostico quitar) {
        this.guardia = guardia;
        this.borrador = borrador;
        this.quitar = quitar;
    }

    @Override
    @Transactional(readOnly = true)
    public VistaPreviaDeImportacion revisar(UserId actor, String texto, Modo modo) {
        guardia.exigirQuePuedaAdministrar(actor);
        LecturaDeImportacion lectura = leer(texto, modo);
        List<PreguntaDelDiagnostico> activas = borrador.cargar().preguntas();
        Map<Integer, Integer> conteos = borrador.respuestasPorPregunta();
        int conRespuestas = (int) activas.stream().filter(p -> conteos.getOrDefault(p.id(), 0) > 0).count();
        return new VistaPreviaDeImportacion(lectura, activas.size(), conRespuestas);
    }

    @Override
    @Transactional
    public ResultadoDeImportacion importar(UserId actor, String texto, Modo modo) {
        guardia.exigirQuePuedaAdministrar(actor);
        LecturaDeImportacion lectura = leer(texto, modo);
        if (lectura.tieneErrores()) {
            throw new ImportacionConErroresException(lectura.errores());
        }
        int eliminadas = 0;
        int retiradas = 0;
        if (modo == Modo.REEMPLAZAR) {
            var resultado = reemplazar();
            eliminadas = resultado.eliminadas();
            retiradas = resultado.retiradas();
        }
        int secciones = aplicar(lectura);
        return new ResultadoDeImportacion(secciones, lectura.preguntas().size(), eliminadas, retiradas);
    }

    // ---------------------------------------------------------------------------------------------- lectura

    private LecturaDeImportacion leer(String texto, Modo modo) {
        Map<String, List<OpcionDelDiagnostico>> existentes = new HashMap<>();
        List<ErrorDeImportacion> choques = new ArrayList<>();
        LecturaDeImportacion lectura;
        if (modo == Modo.ANADIR) {
            borrador.cargar().preguntas().forEach(p -> existentes.put(sinPrefijo(p.clave()), p.opciones()));
            lectura = PlantillaDeImportacion.leer(texto, existentes);
            java.util.Set<String> usadas = clavesYaUsadas();
            lectura.preguntas().stream().filter(p -> p.clave() != null && usadas.contains(PREFIJO_DE_LA_CLAVE + p.clave()))
                    .forEach(p -> choques.add(new ErrorDeImportacion(p.fila(), "Clave", "La clave «" + p.clave() + "» ya existe en el diagnóstico")));
        } else {
            lectura = PlantillaDeImportacion.leer(texto, existentes);
        }
        if (choques.isEmpty()) {
            return lectura;
        }
        List<ErrorDeImportacion> errores = new ArrayList<>(lectura.errores());
        errores.addAll(choques);
        return new LecturaDeImportacion(lectura.preguntas(), errores, lectura.avisos());
    }

    private java.util.Set<String> clavesYaUsadas() {
        java.util.Set<String> claves = new java.util.HashSet<>();
        borrador.cargar().preguntas().forEach(p -> claves.add(p.clave()));
        borrador.retiradas().forEach(p -> claves.add(p.clave()));
        return claves;
    }

    // ---------------------------------------------------------------------------------------------- aplicar

    private ResultadoDeQuitar reemplazar() {
        FichaDelDiagnostico ficha = borrador.cargar();
        List<Integer> ids = ficha.preguntas().stream().map(PreguntaDelDiagnostico::id).toList();
        // Al reemplazar, las protegidas también se van: el archivo trae las suyas (si no, publicar avisa que falta el consentimiento).
        var resultado = quitar.quitar(ids, true, true);
        borrador.liberarClaves(borrador.retiradas().stream().map(PreguntaDelDiagnostico::id).toList());
        for (SeccionDelDiagnostico s : ficha.secciones()) {
            if (!s.esPortada()) {
                borrador.eliminarSeccion(s.id());
            }
        }
        return new ResultadoDeQuitar(resultado.eliminadas(), resultado.retiradas());
    }

    /** Crea lo que falte de secciones y todas las preguntas, en el orden del archivo. Devuelve cuántas secciones usa el archivo. */
    private int aplicar(LecturaDeImportacion lectura) {
        Map<String, Integer> seccionPorTitulo = new LinkedHashMap<>();
        borrador.cargar().secciones().stream().filter(s -> !s.esPortada()).forEach(s -> seccionPorTitulo.put(plano(s.titulo()), s.id()));
        Map<String, Integer> idPorClave = new HashMap<>();
        borrador.cargar().preguntas().forEach(p -> idPorClave.put(sinPrefijo(p.clave()), p.id()));
        for (PreguntaImportada importada : lectura.preguntas()) {
            int seccionId = seccionPorTitulo.computeIfAbsent(plano(importada.seccion()), t -> crearSeccion(importada.seccion()));
            PreguntaDelDiagnostico base = importada.pregunta();
            CondicionDeVisibilidad condicion = importada.condicionClave() == null ? null
                    : new CondicionDeVisibilidad(idPorClave.get(importada.condicionClave()), importada.condicionValor());
            PreguntaDelDiagnostico guardada = borrador.guardarPregunta(PreguntaDelDiagnostico.nueva(seccionId, -1,
                    importada.clave() == null ? null : PREFIJO_DE_LA_CLAVE + importada.clave(), base.texto(), base.ayuda(), base.tipo(), base.requerida(),
                    base.maximoOpciones(), base.opciones(), base.escala(), condicion, base.alimenta(), base.formaciones(), base.precarga()));
            if (importada.clave() != null) {
                idPorClave.put(importada.clave(), guardada.id());
            }
        }
        return (int) lectura.preguntas().stream().map(p -> plano(p.seccion())).distinct().count();
    }

    private int crearSeccion(String titulo) {
        return borrador.crearSeccion("s_" + UUID.randomUUID().toString().substring(0, 8), titulo, null).id();
    }

    private static String sinPrefijo(String clave) {
        return clave != null && clave.startsWith(PREFIJO_DE_LA_CLAVE) ? clave.substring(PREFIJO_DE_LA_CLAVE.length()) : clave;
    }

    private static String plano(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private record ResultadoDeQuitar(int eliminadas, int retiradas) {
    }
}
