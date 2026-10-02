# ONX phone — proyecto iOS

Base de aplicación nativa para iPhone con el diseño y el crédito «ONX phone · por roclic». Cada jugador introduce su enlace personal. No contiene enlaces, sesiones, certificados de Apple ni claves de ningún usuario.

**Esto es código fuente, no una IPA instalable. No se ha compilado con Xcode ni probado en iPhone.** Se ha comprobado que el proyecto Xcode se puede analizar, que existen sus recursos y que el script del icono usa rutas correctamente citadas. El detector JavaScript compartido dispone de pruebas representativas en la versión Android; eso no valida WKWebView ni iOS.

## Compatibilidad prevista

El proyecto tiene mínimo **iOS 15**, para disponer de permisos de captura multimedia en WKWebView. Está preparado para tamaños de iPhone y orientación vertical/horizontal. No promete funcionar en todas las versiones históricas, ni certifica modelos o versiones recientes sin una prueba física. No reemplaza la APK Android 0.5, que sigue disponible por separado.

## Funciones escritas en la base iOS

- Inicio directo en el celular si hay enlace guardado; menú accesible mediante llavecita.
- Enlace guardado en Keychain y cookies persistentes de WKWebView; cierre de sesión local.
- Navegación de ONX/Steam mediante HTTPS y lista explícita de dominios; enlace personal no se abre en apps externas.
- Misma imagen proporcionada, paleta morada, crédito discreto y ajuste geométrico del celular al espacio disponible.
- Estado de internet, sesión vencida según la página y reconexión con espera creciente al volver la red. La reconexión se restringe a la app activa y evita la autenticación, llamada entrante o audio detectado.
- Micrófono/cámara mediante consentimiento de iOS, si la web lo solicita. Captura de voz, videollamada, sesión Steam y selección de archivos requieren prueba real.
- Detector de llamadas y mensajes de las tarjetas de ONX mientras la web está activa, deduplicación y acciones de contestar/rechazar asociadas a la llamada concreta.
- Pantalla de llamada dentro de la app, tonos Suave/Digital originales, vibración y avisos locales con acciones. El sonido local respeta las restricciones de iOS y no equivale a un timbre nativo de CallKit bloqueado.
- Opciones para avisar llamadas, sonido, vibración, mensajes/correos, otros avisos y sonido de avisos.
- Solicitud de altavoz/automática y selector de rutas del sistema. Bluetooth/auricular/voz dependen de la sesión de audio web y necesitan prueba real; no se garantiza el mismo control de salida que en Android.

Los sonidos propios de ONX se configuran en ONX; los ajustes nativos no silencian automáticamente el audio de la web.

## Llamadas como el teléfono normal: integración pendiente

**Con la app bloqueada, suspendida o cerrada, esta base no puede detectar por sí sola las nuevas llamadas y mensajes de ONX.** No se ha habilitado un modo de audio falso para mantener viva la web. iOS puede suspenderla; tampoco se registra PushKit sin un servidor real.

`ONXPhone/CallKitAdapter.swift` contiene el adaptador nativo con reporte de llamada entrante, respuesta, fin, silencio y activación/desactivación del audio. Está deliberadamente desconectado del celular web: sus acciones exigen confirmación del servicio real antes de responder al sistema. No constituye una integración terminada.

Para completar recepción bloqueada hace falta:

1. Un servidor que reciba eventos reales del personaje de ONX de forma autorizada, también mientras el móvil no ejecuta la web. No basta con que exista un endpoint WebSocket: hay que verificar autenticación, protocolo, deduplicación, fin de llamadas y permisos. Puede ser una integración con ONX o un puente autorizado; no existe en este proyecto.
2. Cuenta Apple Developer, identificador de app, credenciales APNs guardadas en el servidor y firma/provisión con capacidad Push Notifications.
3. Registro PushKit VoIP en la app, envío seguro del token del dispositivo al servidor y notificaciones APNs de llamadas reales. Hay que reportar la llamada a CallKit de inmediato desde el callback, antes de esperar a cargar la web. Token, UUID y estados deben tener contrato real, no datos ficticios.
4. Señalización y audio capaces de contestar, rechazar, silenciar y finalizar con la app en segundo plano. WKWebView no reemplaza automáticamente un motor nativo de llamadas. Adaptar el audio/servicio de ONX requiere acceso y pruebas del protocolo real.
5. APNs de mensajes ordinarios separados de las notificaciones VoIP. PushKit solo se usa para llamadas reales.
6. Pruebas en iPhone bloqueado, suspensión, red perdida, cierre, llamadas simultáneas, silencio/Concentración, permisos denegados y fin remoto. La respuesta a una llamada vencida debe fallar de forma segura.

Referencia oficial: https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit

## Compilación sin Mac propio

Puedes usar un entorno macOS remoto. Se incluye `.github/workflows/ios-check.yml`, un workflow manual/de comprobación que compila para simulador sin firma usando un runner macOS de GitHub Actions. **No se ha publicado ni ejecutado**; no hay repositorio ni cuenta conectados. No compra recursos, no envía tu enlace y no crea una IPA para instalar. Revisa los límites/coste de tu cuenta antes de ejecutar trabajos remotos.

Para usarlo, crea un repositorio con el contenido de esta carpeta en su raíz, incluidos `.github/` y `ONXPhone.xcodeproj/`, y ejecuta Check ONX phone iOS. Un fallo de compilación debe corregirse antes de distribuir. La firma y exportación para dispositivos/TestFlight requieren otra etapa con credenciales/provisión Apple; no están configuradas.

En un Mac con Xcode moderno: abre `ONXPhone.xcodeproj`, elige el esquema ONXPhone y compila primero para simulador. Para un iPhone físico elige tu Team y un identificador de app disponible; para la recepción nativa añade las capacidades y servidor descritos arriba. El icono 1024 × 1024 se genera con `sips` durante el build a partir de la imagen incluida. No se necesita XcodeGen ni una dependencia de aplicación externa.

`generate_project.py` regenera proyecto, Info.plist y tonos con Python estándar; no compila Swift. No lo ejecutes sobre personalizaciones de proyecto que quieras conservar sin revisar sus cambios.

## Estado de paridad

| Función | Android 0.5 | Base iOS |
|---|---|---|
| Nombre, imagen, estética y crédito | APK compilada | Código escrito, sin prueba nativa |
| Enlace y sesión persistentes | APK compilada | Keychain/WKWebView escritos |
| Apertura directa y llavecita | APK compilada | Código escrito |
| Adaptación al espacio | Detector probado con DOM | Detector compartido, WKWebView pendiente |
| Voz, altavoz y Bluetooth | Integración, prueba física pendiente | Integración web/rutas, prueba física pendiente |
| Mensajes/llamadas con web activa | Detector probado con DOM | Detector conectado a WKWebView, sin prueba nativa |
| Llamada bloqueado como llamada normal | Mecanismo de presentación preparado; requiere prueba física | Pendiente de servidor, PushKit/CallKit y motor de audio |
| App instalable | APK firmada | No hay IPA firmada |

No se presenta este código como equivalente completo a Android ni como una aplicación ya funcional en todos los iPhone.

## Comprobaciones adicionales del puente iOS

Se aisló el transporte en `ONXPhone/ios-transport.js` y se probó con MessagePorts reales de Node y DOM representativo: conexión, llamada, contestar, rechazar, identificador caducado, fin, reinicialización del canal y rechazo de orígenes HTTP, otro dominio o puerto no permitido. Esto no ejecuta WKWebView ni confirma compatibilidad nativa.

Las pruebas reproducibles están en `tests/`: ejecuta `npm ci` y `npm test` dentro de esa carpeta. El workflow macOS las ejecuta además de intentar compilar Swift. El workflow sigue sin ejecutarse y no se ha generado una IPA.

El cierre de sesión bloquea eventos de la página anterior, cancela reintentos y avisos, detiene la página y espera a borrar los datos antes de permitir una nueva conexión. La instalación y el flujo nativo siguen pendientes de pruebas.

Actualmente no se dispone de Mac, cuenta GitHub ni Apple Developer. Para avanzar a una app instalable hay que obtener acceso autorizado a un entorno macOS y a una identidad/firma Apple, y ejecutar una compilación real. No hay cuentas creadas, repositorios publicados, servicios contratados ni gastos autorizados. La integración con el servidor de llamadas y el audio nativo descrita arriba también sigue pendiente.
