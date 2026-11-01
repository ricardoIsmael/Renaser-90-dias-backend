package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Armadores de fichas y respuestas para las pruebas del diagnóstico. */
final class Fichas {

    static final UserId PERSONA = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000026"));
    static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"));

    private Fichas() {
    }

    static PreguntaDelDiagnostico pregunta(int id, TipoPreguntaOnboarding tipo, boolean requerida) {
        return new PreguntaDelDiagnostico(id, "diag_p" + id, 1, id, "Pregunta " + id, null, tipo, requerida, null, opcionesPara(tipo), null, null, null,
                Set.of(), null, null);
    }

    static PreguntaDelDiagnostico conCondicion(int id, int padre, String valor) {
        PreguntaDelDiagnostico base = pregunta(id, TipoPreguntaOnboarding.TEXTO, true);
        return new PreguntaDelDiagnostico(id, base.clave(), 1, id, base.texto(), null, base.tipo(), true, null, List.of(), null,
                new CondicionDeVisibilidad(padre, valor), null, Set.of(), null, null);
    }

    static PreguntaDelDiagnostico alimenta(int id, DestinoDeRespuesta destino, TipoPreguntaOnboarding tipo, List<OpcionDelDiagnostico> opciones) {
        return new PreguntaDelDiagnostico(id, "diag_p" + id, 1, id, "Pregunta " + id, null, tipo, true, null, opciones, null, null, destino, Set.of(), null,
                null);
    }

    static PreguntaDelDiagnostico paraFormaciones(int id, Formacion... formaciones) {
        return new PreguntaDelDiagnostico(id, "diag_p" + id, 1, id, "Pregunta " + id, null, TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, null,
                null, Set.of(formaciones), null, null);
    }

    static List<OpcionDelDiagnostico> opcionesPara(TipoPreguntaOnboarding tipo) {
        return switch (tipo) {
            case SELECCION_UNICA, SELECCION_MULTIPLE -> List.of(OpcionDelDiagnostico.de("Sí."), OpcionDelDiagnostico.de("No."), OpcionDelDiagnostico.de("Tal vez."));
            case CUADRICULA -> List.of(OpcionDelDiagnostico.de("Fila A"), OpcionDelDiagnostico.de("Fila B"));
            case CASILLA -> List.of(OpcionDelDiagnostico.de("He leído y acepto."));
            default -> List.of();
        };
    }

    static FichaDelDiagnostico ficha(PreguntaDelDiagnostico... preguntas) {
        SeccionDelDiagnostico portada = new SeccionDelDiagnostico(10, SeccionDelDiagnostico.CLAVE_DE_LA_PORTADA, "Diagnóstico · {formacion}",
                "Tiempo estimado: 15 minutos", 0, List.of());
        SeccionDelDiagnostico seccion = new SeccionDelDiagnostico(1, "s1", "Primera", null, 1, List.of(preguntas));
        return new FichaDelDiagnostico(List.of(portada, seccion));
    }

    static Respuesta texto(int preguntaId, String valor) {
        return Respuesta.crear(TipoPreguntaOnboarding.TEXTO, PERSONA, preguntaId, valor, null, null, null, null, null, RELOJ);
    }

    static Respuesta unica(int preguntaId, String valor) {
        return Respuesta.crear(TipoPreguntaOnboarding.SELECCION_UNICA, PERSONA, preguntaId, valor, null, null, null, null, null, RELOJ);
    }

    static Respuesta multiple(int preguntaId, String json) {
        return Respuesta.crear(TipoPreguntaOnboarding.SELECCION_MULTIPLE, PERSONA, preguntaId, null, null, null, null, json, null, RELOJ);
    }

    static Respuesta cuadricula(int preguntaId, String json) {
        return Respuesta.crear(TipoPreguntaOnboarding.CUADRICULA, PERSONA, preguntaId, null, null, null, null, json, null, RELOJ);
    }

    static Respuesta consentimiento(int preguntaId) {
        return Respuesta.crear(TipoPreguntaOnboarding.CASILLA, PERSONA, preguntaId, null, null, true, null, null, null, RELOJ);
    }

    static Map<Integer, Respuesta> por(Respuesta... respuestas) {
        java.util.Map<Integer, Respuesta> m = new java.util.HashMap<>();
        for (Respuesta r : respuestas) {
            m.put(r.preguntaId(), r);
        }
        return m;
    }

    static BigDecimal numero(String n) {
        return new BigDecimal(n);
    }
}
