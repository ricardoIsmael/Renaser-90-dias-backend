# Servicio de voz del orbe: Piper (piper-tts) con la voz es_MX-claude-high, detras de su propio
# servidor HTTP. El backend lo llama con renaser.ia.voz.proveedor=piper (PiperVozAdapter):
#   POST /synthesize  {"text": "...", "length_scale": ..., "noise_scale": ..., "noise_w_scale": ...}
#   -> WAV 22050 Hz mono 16 bits (con Content-Type text/html; el backend valida la cabecera RIFF).
#
# La voz se descarga al construir la imagen, no al arrancar: el contenedor no necesita salida a
# internet y arranca siempre con el mismo modelo.
FROM python:3.12-slim

# Versiones fijas: las mismas con las que se verifico el contrato en desarrollo (2026-09-23).
RUN pip install --no-cache-dir piper-tts==1.8.0 flask==3.1.3

ARG VOZ=es_MX-claude-high
ARG VOZ_URL=https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_MX/claude/high

WORKDIR /voces
RUN python -c "import urllib.request as u; \
u.urlretrieve('${VOZ_URL}/${VOZ}.onnx', '${VOZ}.onnx'); \
u.urlretrieve('${VOZ_URL}/${VOZ}.onnx.json', '${VOZ}.onnx.json')"

RUN useradd --system --no-create-home piper
USER piper

EXPOSE 5055

# Los parametros de voz se repiten aca como default del servidor (los mismos de
# renaser.ia.voz.* en application.yaml); el backend igual los manda en cada pedido, y esos mandan.
CMD ["python", "-m", "piper.http_server", \
     "--host", "0.0.0.0", "--port", "5055", \
     "-m", "/voces/es_MX-claude-high.onnx", \
     "--length-scale", "1.06", "--noise-scale", "0.78", "--noise-w-scale", "0.95", \
     "--sentence-silence", "0.25"]
