package com.renaser.os.users;

import com.renaser.os.users.api.FichaAlAceptar;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Vigila la costura entre la aceptación de solicitudes (spec 025) y la ficha con diagnóstico (spec 026).
 *
 * <p>La 025 no guarda la marca «con diagnóstico»: le pide a {@code onboarding} que la ponga, por {@link FichaAlAceptar}, una interfaz de
 * {@code users.api} que implementa el otro módulo (la dependencia va de {@code onboarding} hacia {@code users}, nunca al revés). Mientras la 026 no
 * esté en la rama, nadie la implementa y «aceptar con diagnóstico» se rechaza con un aviso claro. <b>Cuando las dos specs estén juntas</b> —es decir,
 * cuando exista {@code onboarding.api.FichaDeLaPersonaUseCase}— tiene que haber una implementación: si no, la opción «Con diagnóstico» quedaría
 * sin efecto sin que nadie se entere. Esta prueba lo hace romper el build en ese momento (se omite mientras la 026 no exista).
 */
class FichaAlAceptarImplementadaTest {

    private static final String FICHA_DE_LA_PERSONA = "com.renaser.os.onboarding.api.FichaDeLaPersonaUseCase";

    @Test
    @DisplayName("con la 026 integrada, alguien implementa FichaAlAceptar (si no, «con diagnóstico» no haría nada)")
    void conLa026IntegradaAlguienLaImplementa() {
        assumeTrue(existe(FICHA_DE_LA_PERSONA), "La spec 026 (ficha con diagnóstico) todavía no está en esta rama: integrar la 026 activa esta prueba.");

        var implementaciones = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.renaser.os")
                .stream()
                .filter(clase -> !clase.isInterface() && clase.isAssignableTo(FichaAlAceptar.class))
                .map(JavaClass::getName)
                .toList();

        assertThat(implementaciones)
                .as("Con la 026 integrada, el módulo onboarding debe implementar users.api.FichaAlAceptar y delegar en FichaDeLaPersonaUseCase.asignarDiagnostico")
                .isNotEmpty()
                .allMatch(nombre -> nombre.startsWith("com.renaser.os.onboarding."), "la implementación vive en onboarding");
    }

    private static boolean existe(String clase) {
        try {
            Class.forName(clase, false, FichaAlAceptarImplementadaTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
