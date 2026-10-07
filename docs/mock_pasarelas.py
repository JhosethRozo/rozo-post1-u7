import http.server
import json
import threading
import socket
import sys

def read_request_body(handler):
    if handler.headers.get('Transfer-Encoding', '').lower() == 'chunked':
        chunks = []
        while True:
            line = handler.rfile.readline().strip()
            if not line:
                break
            try:
                chunk_len = int(line, 16)
            except ValueError:
                break
            if chunk_len == 0:
                handler.rfile.readline() # trailing CRLF
                break
            chunks.append(handler.rfile.read(chunk_len))
            handler.rfile.readline() # chunk CRLF
        return b''.join(chunks).decode('utf-8', errors='ignore')
    else:
        length = int(handler.headers.get('Content-Length', 0))
        if length > 0:
            return handler.rfile.read(length).decode('utf-8', errors='ignore')
        return ""

class PagosUdesHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = read_request_body(self)
        print(f"[MOCK 9001 RECIBIDO]: {body}", flush=True)

        if "RECHAZO" in body:
            data = {"idTransaccion": "UDES-RECHAZADA-01", "estadoTransaccion": "RECHAZADA"}
        else:
            data = {"idTransaccion": "UDES-TX-987654", "estadoTransaccion": "APROBADA"}

        response = json.dumps(data).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response)))
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        return

class WompiHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = read_request_body(self)
        print(f"[MOCK 9002 RECIBIDO]: {body}", flush=True)

        if "RECHAZO" in body or "multa-2" in body or "multa-3" in body or "multa-99" in body:
            data = {"reference": "wompi-ref-rechazada", "status": "DECLINED"}
        else:
            data = {"reference": "wompi-ref-554433", "status": "APPROVED"}

        response = json.dumps(data).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response)))
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        return

def make_server(host, port, handler):
    try:
        s = http.server.ThreadingHTTPServer((host, port), handler)
        threading.Thread(target=s.serve_forever, daemon=True).start()
    except Exception as e:
        pass

if __name__ == '__main__':
    make_server('127.0.0.1', 9001, PagosUdesHandler)
    make_server('127.0.0.1', 9002, WompiHandler)
    print("Mock Pasarelas escuchando en puertos 9001 (PagosUDES) y 9002 (Wompi) con soporte chunked.", flush=True)
    
    import time
    while True:
        time.sleep(1)
