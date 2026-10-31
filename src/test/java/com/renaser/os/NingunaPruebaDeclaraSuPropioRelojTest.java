package com.renaser.os;

import com.renaser.os.leadership.application.services.CumplimientoDePrueba;
import com.renaser.os.leadership.application.services.LiderazgoDePrueba;
import com.renaser.os.mentoring.application.services.VistasDelSemaforoDePrueba;
import com.renaser.os.shared.domain.Clock;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Una prueba de integración NO declara su propio {@code Clock} (E-649): usa el {@link RelojDePrueba}
 * compartido ({@code @Autowired RelojDePrueba reloj} y {@code reloj.fijar(...)} en el
 * {@code @BeforeEach}).
 *
 * <p>Cada {@code @Primary Clock} propio, o {@code @MockitoBean Clock}, parte el caché de contextos de
 * Spring: la clase arranca su propio contexto, con su Postgres y su Redis, aunque en lo demás sea
 * igual a las vecinas. Veinticinco clases lo hacían y sumaban 266 s de la suite (130 pruebas). Y con
 * dos {@code @Primary Clock} en el mismo contexto, Spring no sabe cuál elegir.
 *
 * <p>Las tres únicas excepciones son los dobles en memoria de los controladores del líder, del mentor y
 * del semáforo ({@code CumplimientoDePrueba}, {@code LiderazgoDePrueba}, {@code VistasDelSemaforoDePrueba}):
 * los importan pruebas de {@code @WebMvcTest}, que no arrancan contenedores ni el contexto completo y no
 * ven el {@link RelojDePrueba}. Si una de ellas pasa a un contexto completo, debe usar el reloj compartido.
 */
class NingunaPruebaDeclaraSuPropioRelojTest {

    /** Solo las clases de prueba compiladas: las de producción no declaran relojes de prueba. */
    private static final JavaClasses PRUEBAS = new ClassFileImporter()
            .withImportOption(location -> location.contains("/test-classes/"))
            .importPackages("com.renaser.os");

    @Test
    @DisplayName("ningún método @Bean de una prueba devuelve un Clock, salvo el RelojDePrueba compartido")
    void ningunBeanDePruebaEsUnClock() {
        noMethods().that().areAnnotatedWith(Bean.class)
                .and().areNotDeclaredIn(RelojDePruebaConfiguration.class)
                .and().areNotDeclaredIn(CumplimientoDePrueba.class)
                .and().areNotDeclaredIn(LiderazgoDePrueba.class)
                .and().areNotDeclaredIn(VistasDelSemaforoDePrueba.class)
                .should().haveRawReturnType(assignableTo(Clock.class))
                .because("un reloj propio parte el caché de contextos (E-649): usa RelojDePrueba y fíjalo en el @BeforeEach")
                .check(PRUEBAS);
    }

    @Test
    @DisplayName("ningún campo @MockitoBean o @MockitoSpyBean de una prueba es un Clock")
    void ningunMockDeClock() {
        noFields().that().areAnnotatedWith(MockitoBean.class)
                .or().areAnnotatedWith(MockitoSpyBean.class)
                .should().haveRawType(assignableTo(Clock.class))
                .because("un Clock simulado parte el caché de contextos (E-649): usa RelojDePrueba")
                .check(PRUEBAS);
    }
}
