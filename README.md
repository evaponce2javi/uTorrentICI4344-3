# Cómo ejecutar el Proyecto

## Paso solo 1 vez:

### Paso 0 — Verificar que tienes Java instalado
En cualquier terminal, ejecuta:
```
java -version
javac -version
```

Necesitas JDK 11 o superior. Si solo aparece java -version pero no javac, tienes solo el JRE; instala el JDK desde adoptium.net o usa sudo apt install default-jdk en Linux.
Paso 1 — Crear la estructura de carpetas y archivos
Ubícate en la carpeta donde quieras tener el proyecto (por ejemplo ~/proyectos/utorrent) y crea las carpetas:

**Windows PowerShell**:
```
New-Item -ItemType Directory -Force -Path src\utorrent\app, src\utorrent\p2p, src\utorrent\tracker, src\utorrent\modelos, src\utorrent\utils, src\utorrent\protocolo, out, archivos-a-compartir, descargas
```

**Linux/Mac**:

```
mkdir -p src/utorrent/{app,p2p,tracker,modelos,utils,protocolo}
mkdir -p out
mkdir -p archivos-a-compartir
mkdir -p descargas
```


Luego copia cada archivo .java que te entregué a su carpeta correspondiente. Verifica que el package declarado en la primera línea de cada archivo coincida con la carpeta. Por ejemplo:

- src/utorrent/app/AplicacionCliente.java → debe empezar con package utorrent.app;
- src/utorrent/p2p/SesionPar.java → debe empezar con package utorrent.p2p;
- src/utorrent/tracker/ServidorTracker.java → debe empezar con package utorrent.tracker;

### Paso 2 — Compilar todo el proyecto
Desde la raíz del proyecto (la carpeta que contiene src/ y out/):

**Windows PowerShell**:
```
javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName })
```

**Windows CMD**:
```
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
```

**Linux/Mac**:
```
javac -d out $(find src -name "*.java")
```




Si todo está bien, no debe imprimir nada (o solo un par de warnings sobre serial, que son inofensivos). Si imprime errores, revisa que cada archivo tenga el package correcto y que esté en su carpeta correspondiente.

### Paso 3 — Preparar un archivo de prueba para compartir
Crea un archivo cualquiera de menos de 50 MB (recuerda el límite del enunciado). Por ejemplo:

**Windows PowerShell**:
```
# archivo de 2 MB con datos aleatorios
$datos = New-Object byte[] 2097152
(New-Object Random).NextBytes($datos)
[IO.File]::WriteAllBytes("archivos-a-compartir\video.mp4", $datos)
```
**Linux/Mac**:
```
# archivo de 2 MB con datos aleatorios
dd if=/dev/urandom of=archivos-a-compartir/video.mp4 bs=1024 count=2048
```

O simplemente copia cualquier MP3, PDF o imagen pequeña que tengas y renómbrala a algo como video.mp4.

## Ejecución del sistema
Ahora abres 3 terminales separadas, todas ubicadas en la raíz del proyecto. El orden importa: tracker primero, después seeder, finalmente leecher.

### Terminal 1 — Servidor Tracker
```
java -cp out utorrent.tracker.ServidorTracker
```

El programa pedirá tres parámetros. Responde así (presiona ENTER después de cada uno):

```
=== Servidor Tracker BitTorrent ===
Puerto de escucha (sugerido 6969): 6969
Máximo de peers por IP (sugerido 3): 10
Tamaño del pool de hilos (sugerido 32): 32
[Tracker] Escuchando en puerto 6969 (maxParesPorIp=3)
```

**Sobre los parámetros:**

- Puerto 6969 es el estándar de los trackers BitTorrent.
- Máximo de peers por IP = 10: lo subo de 3 a 10 porque al hacer pruebas en localhost todos los peers comparten la misma IP (127.0.0.1) y se acabaría el rate limit anti-Sybil rápidamente. En una red real con IPs distintas, usa 3.
- Pool de 32 hilos: suficiente para decenas de peers concurrentes.

Esta terminal queda bloqueada con el tracker corriendo. No la cierres mientras hagas pruebas.

### Terminal 2 — Usuario 1 (Seeder)
Abre una segunda terminal en la misma carpeta del proyecto y ejecuta:

```
java -cp out utorrent.app.AplicacionCliente
```

El programa pedirá los datos de configuración. Responde así:

```
=========================================
   uTorrent académico — Cliente P2P
=========================================
IP del Tracker (ej. 192.168.1.10): 127.0.0.1
Puerto del Tracker (ej. 6969): 6969
Puerto local de escucha P2P (ej. 6881): 6881
Mi peerId: -UT0001-XxXxXxXxXxXx

Selecciona una opción:
  1. Compartir archivo (Seeder)
  2. Descargar archivo (Leecher)
Opción: 1
Ruta del archivo a compartir: archivos-a-compartir/video.mp4
```

Lo que debería pasar a continuación:
```
[Seeder] Hasheando video.mp4 (2097152 bytes en piezas de 262144 bytes)...
[Seeder] infoHash = 4a8c1f9b3e2d...
[Seeder] piezas   = 8
[ServidorPar] Escuchando peers entrantes en puerto 6881
[Seeder] Compartiendo. Presiona ENTER para detener...
Y en la Terminal 1 (tracker) verás:
[Tracker] iniciado     peer=-UT0001- ip=127.0.0.1 pares_swarm=1
[Tracker] metadatos publicados archivo=video.mp4 ip=127.0.0.1
```
El seeder queda esperando. No presiones ENTER aún: si lo haces, el seeder se detiene. Déjalo así para que pueda servir al leecher.

**Notas sobre los parámetros:**

- *127.0.0.1 es localhost. Si pruebas entre dos máquinas distintas en la misma red, pon la IP LAN del PC donde corre el tracker (algo como 192.168.1.X — averíguala con ipconfig en Windows o ip addr en Linux).*
- *Puerto local de escucha P2P = 6881: cada peer necesita su propio puerto único. Como vamos a tener dos peers en la misma máquina, el seeder usa 6881 y el leecher usará 6882.*
- *Si te equivocas con la ruta del archivo, el programa avisa y termina; vuelves a ejecutarlo.**

### Terminal 3 — Usuario 2 (Leecher)
Abre una tercera terminal en la misma carpeta y ejecuta:

```
java -cp out utorrent.app.AplicacionCliente
```

Responde así:

```
IP del Tracker (ej. 192.168.1.10): 127.0.0.1
Puerto del Tracker (ej. 6969): 6969
Puerto local de escucha P2P (ej. 6881): 6882
Mi peerId: -UT0001-YyYyYyYyYyYy

Selecciona una opción:
  1. Compartir archivo (Seeder)
  2. Descargar archivo (Leecher)
Opción: 2
Nombre del archivo a descargar (ej. video.mp4): video.mp4
Carpeta de destino para la descarga: descargas
```

Nota el cambio: puerto local 6882 (no 6881, que ya lo está usando el seeder).

Lo que verás:
```
[Leecher] Consultando al tracker por 'video.mp4'...
[Leecher] Archivo encontrado: 2097152 bytes en 8 piezas de 262144 bytes
[Leecher] Espacio reservado en descargas/video.mp4
[ServidorPar] Escuchando peers entrantes en puerto 6882
[SesionTorrent] Conectando a 1 peer(s)...
[Leecher] Descarga en curso. Espera mientras se completan las piezas...
[SesionPar] Handshake OK con -UT0001-
[SesionPar] -UT0001- tiene 8/8 piezas
[SesionPar] ✓ Pieza 3/8 verificada (1/8 total)
[SesionPar] ✓ Pieza 1/8 verificada (2/8 total)
[SesionPar] ✓ Pieza 7/8 verificada (3/8 total)
...
[Leecher] Progreso: 8/8 piezas (100.0%)
[Leecher] ✓ Descarga completa: descargas/video.mp4
```
Las piezas se descargan en orden no secuencial gracias a la selección aleatoria del GestorPiezas — esto es lo que demuestra el algoritmo del enunciado.

Verificar que la descarga es correcta
Abre una cuarta terminal (o usa cualquiera de las anteriores tras detener su programa) y compara los hashes:

**Windows PowerShell**:
```
Get-FileHash archivos-a-compartir/video.mp4 -Algorithm SHA1
Get-FileHash descargas/video.mp4 -Algorithm SHA1
```

**Linux/Mac**:
```
sha1sum archivos-a-compartir/video.mp4 descargas/video.mp4
```

Ambos hashes deben ser idénticos. Si lo son, has completado la prueba: el archivo se transfirió bit a bit por el protocolo P2P.
Detener todo

Terminal 3 (leecher): ya terminó cuando el archivo se completó. Si sigue activo (caso del seeder modo descarga), Ctrl+C.
Terminal 2 (seeder): presiona ENTER en la consola, o Ctrl+C.
Terminal 1 (tracker): Ctrl+C.

---

## Variantes para la presentación / demo
Probar concurrencia: dos leechers descargando del mismo seeder
Abre una Terminal 4 mientras la 1, 2 y 3 siguen activas (o reinicias el seeder y arrancas dos leechers en paralelo):
```
java -cp out utorrent.app.AplicacionCliente
Configuración del segundo leecher:
```
- IP Tracker: 127.0.0.1
- Puerto Tracker: 6969
- Puerto local: 6883 (distinto a 6881 del seeder y 6882 del primer leecher)
- Opción: 2
- Nombre: video.mp4
- Destino: descargas2 (otra carpeta para no pisar)

En la Terminal 1 (tracker) verás cómo se registran los 3 peers en el swarm. En la Terminal 2 (seeder) verás dos Handshake OK con peerIds distintos, y los request se intercalan entre ambos leechers — esto demuestra el servidor multi-hilo.

**Probar tolerancia a fallos: matar el tracker durante la descarga.**

Mientras un leecher está descargando, presiona Ctrl+C en la Terminal 1 (tracker). El leecher seguirá descargando porque ya tiene la conexión P2P abierta con el seeder; solo fallarán los announce periódicos (verás los Backoff exponencial 5s/15s/30s en el log).

**Probar tolerancia a fallos: matar el seeder durante la descarga**

Mientras un leecher está descargando, presiona Ctrl+C en la Terminal 2 (seeder). El leecher detectará la desconexión:
```
[SesionPar] Peer -UT0001- desconectado: EOF
```
Y la pieza que estaba descargando se re-encolará automáticamente para que la pueda intentar otro peer.

### Errores comunes y cómo resolverlos

**ClassNotFoundException: utorrent.app.AplicacionCliente**
Estás ejecutando desde una carpeta incorrecta. Vuelve a la raíz del proyecto (la que contiene src/ y out/) antes de ejecutar java -cp out ....

**Address already in use**
El puerto que pediste ya lo está usando otro programa (o un Java zombi de una prueba anterior). Cambia el puerto o ejecuta pkill -9 java (Linux/Mac) o cierra todas las terminales y vuelve a empezar.

**El tracker dice Rate limit excedido para IP 127.0.0.1**
Estás haciendo demasiadas pruebas seguidas en localhost y el contador anti-Sybil llegó al límite. Espera 60 segundos a que se reinicie la ventana, o sube el Máximo de peers por IP al iniciar el tracker.

**El leecher dice El tracker no conoce el archivo solicitado**
Estás pidiendo un nombre que ningún seeder ha publicado. Verifica que el seeder haya completado el metadatos publicados antes de iniciar el leecher.

**El handshake falla con infoHash distinto al esperado**
Esto pasa si dos seeders distintos comparten archivos con el mismo nombre pero contenido diferente. El leecher recibe los metadatos del primero pero conecta al segundo. Solución: limpia el tracker (reinícialo) y vuelve a publicar.

