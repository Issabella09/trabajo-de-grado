<div align="center">

# EVA — Asistente Inteligente por Voz para Personas con Discapacidad Visual

**Trabajo de Grado — Ingeniería de Sistemas**  

</div>

---

## Descripción

EVA es un asistente de voz nativo para Android desarrollado en Kotlin, diseñado exclusivamente para personas con discapacidad visual. Permite interactuar con el dispositivo móvil de forma completamente autónoma mediante comandos de voz, sin necesidad de tocar la pantalla en ningún momento.

El asistente se activa pronunciando **"Hola EVA"** y puede ejecutar comandos como enviar mensajes por WhatsApp, leer notificaciones, describir el entorno visual, consultar la hora, abrir aplicaciones y más.

---

## Características principales

- 🎙️ **Detección de hotword offline** — Reconoce "hola EVA" sin conexión a internet usando Vosk
- 🗣️ **Reconocimiento de comandos en lenguaje natural** — Soporta variaciones naturales del español colombiano
- 🔊 **Síntesis de voz** — Todas las respuestas se comunican mediante TextToSpeech
- 📲 **Lectura automática de notificaciones** — Lee en voz alta las notificaciones de cualquier app al recibirlas
- 📷 **Descripción del entorno visual** — Analiza lo que ve la cámara y lo vocaliza cada 3,5 segundos usando ML Kit
- 💬 **Mensajería por voz** — Abre WhatsApp con un contacto y mensaje predactado mediante un único comando
- 📱 **Apertura de aplicaciones** — Lanza cualquier app instalada por nombre
- 🔐 **Autenticación** — Login, registro y recuperación de contraseña con Firebase Auth

---

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Plataforma | Android (API 29+) |
| Hotword offline | [Vosk](https://alphacephei.com/vosk/) + modelo `vosk-model-small-es-0.42` |
| Reconocimiento de comandos | Android `SpeechRecognizer` (Google Speech, `es-CO`) |
| Síntesis de voz | Android `TextToSpeech` (`es_CO`) |
| Descripción visual | CameraX + ML Kit Image Labeling |
| Notificaciones | `NotificationListenerService` |
| Accesibilidad del sistema | `AccessibilityService` |
| Autenticación | Firebase Authentication |
| Base de datos | Firebase Firestore |
| Mensajería | Intent URI scheme `wa.me` (WhatsApp) |
| UI | Material Design 3, tema oscuro |
| Servicio en segundo plano | `ForegroundService` con `START_STICKY` |

---

## Arquitectura

```
EVA
├── Capa de presentación
│   ├── MainActivity              (login / registro)
│   ├── AsistenteVozNuevoActivity (pantalla principal del asistente)
│   ├── DescripcionCamaraActivity (descripción del entorno)
│   ├── OverlayLauncherActivity   (lanzador via PendingIntent)
│   └── ConfiguracionActivity     (ajustes de usuario)
│
├── Servicios Android
│   ├── EvaListeningService       (orquestador principal — ForegroundService)
│   ├── NotificationReaderService (NotificationListenerService)
│   └── LecturaPantallaService    (AccessibilityService)
│
├── Módulos de IA (on-device)
│   ├── VoskHotwordDetector       (detección offline de "hola EVA")
│   ├── SpeechRecognizer          (reconocimiento de comandos)
│   ├── TextToSpeech              (síntesis de respuestas)
│   └── CameraX + ML Kit          (descripción del entorno visual)
│
└── Servicios externos
    ├── Firebase Auth + Firestore  (usuarios y configuración)
    └── WhatsApp Intent (wa.me)    (mensajería)
```

---

## Comandos soportados

| Comando de voz | Acción |
|---|---|
| `"hola EVA"` | Activa el asistente desde cualquier estado |
| `"Envía un mensaje por WhatsApp a [nombre] que diga [mensaje]"` | Abre WhatsApp con mensaje predactado |
| `"Abre [nombre de la app]"` | Lanza la aplicación |
| `"¿Qué hora es?"` / `"Dime la hora"` | Vocaliza la hora actual |
| `"¿Qué día es hoy?"` / `"¿Cuál es la fecha?"` | Vocaliza la fecha actual |
| `"Describe el entorno"` / `"Descripción"` | Activa descripción visual continua |

---

## Requisitos

- Android 10 o superior (API 29+)
- Dispositivo con micrófono y cámara trasera
- Servicios de Google instalados (para SpeechRecognizer y ML Kit)
- Conexión a internet para reconocimiento de comandos y Firebase
- Permisos requeridos:
  - `RECORD_AUDIO`
  - `READ_CONTACTS`
  - `CAMERA`
  - `FOREGROUND_SERVICE`
  - `SYSTEM_ALERT_WINDOW` (overlay)
  - `BIND_NOTIFICATION_LISTENER_SERVICE` (configuración manual en ajustes)

---

## Instalación

### Desde el APK de debug

1. Descarga el APK desde [Releases](../../releases)
2. Activa la instalación desde fuentes desconocidas en tu dispositivo
3. Instala el APK
4. Al iniciar, concede los permisos solicitados
5. Ve a **Ajustes → Accesibilidad → Notificaciones** y activa EVA para lectura de notificaciones

### Desde Android Studio

```bash
git clone https://github.com/Issabella09/trabajo-de-grado.git
cd trabajo-de-grado
```

1. Abre el proyecto en Android Studio
2. Conecta tu dispositivo Android con depuración USB activada
3. Agrega tu archivo `google-services.json` en la carpeta `app/` (Firebase)
4. Ejecuta con ▶ Run o `Shift + F10`

> ⚠️ El modelo de Vosk (`vosk-model-small-es-0.42`, ~50 MB) se incluye en `app/src/main/assets/`. Si no está presente, descárgalo desde [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models) y colócalo ahí.

---

## Estructura del proyecto

```
app/
├── src/main/
│   ├── assets/
│   │   └── vosk-model-small-es-0.42/   ← modelo de reconocimiento offline
│   ├── java/com/trabajogrado/asistente/
│   │   ├── MainActivity.kt
│   │   ├── AsistenteVozNuevoActivity.kt
│   │   ├── EvaListeningService.kt
│   │   ├── VoskHotwordDetector.kt
│   │   ├── NotificationReaderService.kt
│   │   ├── OverlayLauncherActivity.kt
│   │   └── ...
│   └── res/
│       ├── layout/
│       ├── values/
│       │   ├── colors.xml              ← #1A1A1A fondo, #F76707 naranja
│       │   └── themes.xml
│       └── drawable/
└── google-services.json                ← NO incluido en el repo (agregar manualmente)
```

---

## Limitaciones conocidas

- **WhatsApp:** Solo predacta el mensaje; el envío automático no es posible por restricciones de seguridad de la plataforma. Se requiere un toque final del usuario.
- **Hotword en ruido elevado:** La tasa de detección disminuye en entornos con ruido de fondo alto (música, multitudes). El modelo compacto de 50 MB tiene menor precisión que modelos de mayor tamaño.
- **Permisos de notificaciones:** Requiere configuración manual en los ajustes del sistema operativo.

---

## Autora

**Isabella Rebellón Medina**  

---
