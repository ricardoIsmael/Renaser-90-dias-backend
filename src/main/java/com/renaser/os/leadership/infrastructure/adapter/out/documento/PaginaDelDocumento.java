package com.renaser.os.leadership.infrastructure.adapter.out.documento;

/**
 * La hoja en la que se dibuja todo documento (spec 056): HTML de un solo archivo, sin scripts ni recursos de afuera, en A4 y listo para
 * imprimir o guardar como PDF desde el navegador. El estado se pinta con color pero siempre lleva su palabra y su símbolo.
 */
final class PaginaDelDocumento {

    private static final String ESTILO = """
            @page { size: A4; margin: 16mm; }
            * { box-sizing: border-box; }
            body { margin: 0; font-family: -apple-system, "Segoe UI", Roboto, Arial, sans-serif; color: #1e1b18; background: #ffffff; line-height: 1.45; font-size: 14px; }
            main { max-width: 760px; margin: 0 auto; padding: 24px 16px; }
            h1 { font-size: 24px; margin: 0 0 4px; }
            h2 { font-size: 16px; margin: 0 0 8px; padding-bottom: 4px; border-bottom: 1px solid #d8d3ca; }
            section { margin: 0 0 18px; page-break-inside: avoid; }
            .marca { letter-spacing: 6px; font-size: 12px; color: #4a453d; margin: 0 0 12px; }
            .meta { margin: 0; display: grid; grid-template-columns: max-content 1fr; gap: 2px 12px; }
            .meta dt { color: #4a453d; }
            .meta dd { margin: 0; }
            .estado { display: inline-block; padding: 6px 12px; border-radius: 6px; border: 2px solid #4a453d; font-weight: 700; font-size: 16px; }
            .estado-rojo { border-color: #b3261e; background: #fdecea; }
            .estado-amarillo { border-color: #8a5a00; background: #fff4d6; }
            .estado-verde { border-color: #1b6e3a; background: #e3f4e8; }
            .estado-sin-datos, .estado-no-se-mide { border-color: #6b665d; background: #f1efea; }
            .cifras { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
            .cifra { border: 1px solid #d8d3ca; border-radius: 6px; padding: 8px; }
            .cifra .valor { display: block; font-size: 22px; font-weight: 700; font-variant-numeric: tabular-nums; }
            .cifra .cambio { display: block; color: #4a453d; font-size: 12px; }
            table { width: 100%; border-collapse: collapse; }
            th, td { border: 1px solid #d8d3ca; padding: 4px 6px; text-align: left; vertical-align: top; }
            th { background: #f1efea; }
            td.num { text-align: center; font-variant-numeric: tabular-nums; }
            blockquote { margin: 0 0 8px; padding: 4px 10px; border-left: 3px solid #d8d3ca; }
            ul { margin: 0; padding-left: 20px; }
            .vacio { font-weight: 700; padding: 12px; border: 1px dashed #6b665d; border-radius: 6px; }
            .segunda-pagina { page-break-before: always; }
            @media print { main { padding: 0; } a { color: inherit; text-decoration: none; } }
            """;

    private PaginaDelDocumento() {
    }

    /** @param titulo ya escapado; @param cuerpo las secciones, ya escapadas */
    static String armar(String titulo, String cuerpo) {
        return "<!doctype html>\n<html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + titulo + "</title><style>\n" + ESTILO + "</style></head>\n<body><main>\n" + cuerpo + "</main></body></html>\n";
    }
}
