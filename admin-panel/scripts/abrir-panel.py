"""Abre el panel en el navegador, firmando cada request con SigV4.

La Function URL es AWS_IAM: no responde a nadie que no venga con credenciales de esta cuenta.
Eso es justo lo que la deja fuera del alcance de internet, y es tambien lo que un navegador no
sabe hacer solo. Este ayudante cierra ese hueco: escucha en 127.0.0.1, firma cada request con
el perfil `renaser` y la reenvia a la Function URL.

    python admin-panel/scripts/abrir-panel.py

Despues se abre http://127.0.0.1:8788 en cualquier navegador. Se corta con Ctrl+C.

Las credenciales las maneja botocore (el mismo mecanismo que la CLI): este archivo no las lee,
no las imprime y no las guarda.
"""

import http.server
import sys
import urllib.error
import urllib.request
import webbrowser

import boto3
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

PERFIL = "renaser"
REGION = "us-east-1"
FUNCION = "renaser-admin-panel"
PUERTO = 8788

# El panel emite cookies `__Host-` con `Secure`, que es lo correcto sobre HTTPS. Al pasar por
# 127.0.0.1 sobre HTTP, no todos los navegadores las aceptan (Safari nunca), asi que el ayudante
# les cambia el nombre y les saca `Secure` en el camino de vuelta, y deshace el cambio en el de
# ida. El codigo de la Lambda queda estricto: el arreglo vive aca, que es la maquina de confianza.
COOKIES = {
    "__Host-renaser_sesion": "renaser_sesion_local",
    "__Host-renaser_csrf": "renaser_csrf_local",
}

sesion = boto3.Session(profile_name=PERFIL, region_name=REGION)
credenciales = sesion.get_credentials()
if credenciales is None:
    sys.exit(f"No hay credenciales para el perfil '{PERFIL}'.")

base = sesion.client("lambda").get_function_url_config(FunctionName=FUNCION)["FunctionUrl"].rstrip("/")
firmante = SigV4Auth(credenciales, "lambda", REGION)

# Los 303 del panel los tiene que ver el navegador, no este ayudante.
abridor = urllib.request.build_opener(type("SinRedireccion", (urllib.request.HTTPRedirectHandler,), {
    "redirect_request": lambda *_: None,
})())


def a_la_lambda(cookie):
    for real, local in COOKIES.items():
        cookie = cookie.replace(f"{local}=", f"{real}=")
    return cookie


def al_navegador(set_cookie):
    for real, local in COOKIES.items():
        set_cookie = set_cookie.replace(f"{real}=", f"{local}=")
    return set_cookie.replace("; Secure", "")


class Ayudante(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, formato, *args):
        # Sin datos personales en la consola: solo metodo, ruta y codigo.
        sys.stderr.write(f"  {self.command} {self.path} -> {args[1] if len(args) > 1 else ''}\n")

    def _reenviar(self):
        largo = int(self.headers.get("Content-Length") or 0)
        cuerpo = self.rfile.read(largo) if largo else None

        # Se firma un juego minimo de cabeceras. La cookie viaja SIN firmar a proposito: SigV4
        # solo verifica las que estan en SignedHeaders, y dejarla afuera evita que el navegador
        # invalide la firma cada vez que cambia una cookie.
        firmada = AWSRequest(method=self.command, url=base + self.path, data=cuerpo,
                             headers={"content-type": self.headers.get("Content-Type", "")}
                             if cuerpo else {})
        firmante.add_auth(firmada)

        cabeceras = dict(firmada.headers)
        if self.headers.get("Cookie"):
            cabeceras["Cookie"] = a_la_lambda(self.headers["Cookie"])

        try:
            arriba = abridor.open(urllib.request.Request(base + self.path, data=cuerpo,
                                                          headers=cabeceras, method=self.command),
                                   timeout=30)
            codigo, cabeceras_arriba, contenido = arriba.status, arriba.headers, arriba.read()
        except urllib.error.HTTPError as error:
            codigo, cabeceras_arriba, contenido = error.code, error.headers, error.read()

        self.send_response(codigo)
        for nombre in ("Content-Type", "Location", "Content-Security-Policy", "Referrer-Policy",
                        "X-Content-Type-Options", "Cache-Control"):
            if cabeceras_arriba.get(nombre):
                self.send_header(nombre, cabeceras_arriba[nombre])
        for galleta in cabeceras_arriba.get_all("Set-Cookie") or []:
            self.send_header("Set-Cookie", al_navegador(galleta))
        self.send_header("Content-Length", str(len(contenido)))
        self.end_headers()
        self.wfile.write(contenido)

    do_GET = _reenviar
    do_POST = _reenviar


if __name__ == "__main__":
    servidor = http.server.ThreadingHTTPServer(("127.0.0.1", PUERTO), Ayudante)
    local = f"http://127.0.0.1:{PUERTO}/"
    print(f"Panel en {local}   (firmando contra {base})")
    print("Ctrl+C para cortar.\n")
    try:
        webbrowser.open(local)
    except Exception:
        pass
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\nCortado.")
