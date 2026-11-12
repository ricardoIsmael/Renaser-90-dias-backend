package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Formacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Una ficha FIJA del diagnóstico para las pruebas de la exportación (spec 050, F; SC-008): con comas, comillas, saltos de
 * línea en la ayuda, opciones con código, escala, condición, destino y formaciones.
 */
final class FichasParaLaExportacion {

    private FichasParaLaExportacion() {
    }

    static FichaDelDiagnostico deSiempre() {
        PreguntaDelDiagnostico p1 = new PreguntaDelDiagnostico(1, "p1", 1, 1, "¿Cuál es tu nombre completo?", null,
                TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null, null, null, Set.of(), null, null);
        PreguntaDelDiagnostico p2 = new PreguntaDelDiagnostico(2, "p2", 1, 2, "¿Tienes un negocio, o un proyecto?", "Elige una\nsola opción, \"la principal\"",
                TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                List.of(new OpcionDelDiagnostico("SI", "Sí."), new OpcionDelDiagnostico("NO", "No.")), null, null,
                null, Set.of(Formacion.ESCUELA, Formacion.DIPLOMADO), null, null);
        PreguntaDelDiagnostico p2a = new PreguntaDelDiagnostico(3, "p2a", 2, 1, "¿A qué se dedica?", null,
                TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, new CondicionDeVisibilidad(2, "Sí."),
                DestinoDeRespuesta.OBJETIVO_NEGOCIO, Set.of(), null, null);
        PreguntaDelDiagnostico p3 = new PreguntaDelDiagnostico(4, "p3", 2, 2, "¿Qué tanto?", "De 1 a 10",
                TipoPreguntaOnboarding.ESCALA, true, null, List.of(), new EscalaDelDiagnostico(1, 10, "Poco", "Mucho"),
                null, null, Set.of(Formacion.CERTIFICACION), null, null);
        PreguntaDelDiagnostico p4 = new PreguntaDelDiagnostico(5, "p4", 2, 3, "¿Prioridad principal?", null,
                TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                List.of(new OpcionDelDiagnostico("RELACIONES", "Relaciones."), new OpcionDelDiagnostico("CUERPO", "Cuerpo."),
                        new OpcionDelDiagnostico("TRABAJO", "Trabajo.")), null, null, DestinoDeRespuesta.PRIORIDAD_PRINCIPAL,
                Set.of(), null, null);
        PreguntaDelDiagnostico p5 = new PreguntaDelDiagnostico(6, "p5", 2, 4, "Máximo dos", null,
                TipoPreguntaOnboarding.SELECCION_MULTIPLE, false, 2,
                List.of(new OpcionDelDiagnostico("A", "Uno."), new OpcionDelDiagnostico("B", "Dos."), new OpcionDelDiagnostico("C", "Tres.")),
                null, null, null, Set.of(), null, null);
        List<SeccionDelDiagnostico> secciones = new ArrayList<>();
        secciones.add(new SeccionDelDiagnostico(10, SeccionDelDiagnostico.CLAVE_DE_LA_PORTADA, "Diagnóstico · {formacion}",
                "Tiempo estimado: 15 minutos", 0, List.of()));
        secciones.add(new SeccionDelDiagnostico(1, "s1", "Conociéndote, de verdad", "Una descripción", 1, List.of(p1, p2)));
        secciones.add(new SeccionDelDiagnostico(2, "s2", "Tu \"vida\" hoy", null, 2, List.of(p2a, p3, p4, p5)));
        return new FichaDelDiagnostico(secciones);
    }
}
