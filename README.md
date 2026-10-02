# ONX phone · por roclic

Proyecto para abrir el celular web personal de ONX en Android y iPhone. Cada jugador guarda su propio enlace. No contiene sesiones personales, tokens de jugador ni claves de firma.

## iOS

El proyecto Xcode está en la raíz: `ONXPhone.xcodeproj`. La base tiene mínimo iOS 15, interfaz SwiftUI/WKWebView, Keychain, adaptación de tamaño, avisos, tonos y el crédito a roclic. Su estado técnico y límites están en [IOS-ESTADO.md](IOS-ESTADO.md).

GitHub Actions ejecuta **Check ONX phone iOS**: prueba el puente JavaScript, compila para simulador sin firma y guarda el registro y la aplicación de simulador como artefactos. **Un artefacto de simulador no se instala en un iPhone**. La firma para dispositivos o TestFlight requiere configurar Apple Developer y una compilación de distribución por separado.

Las llamadas con iPhone bloqueado siguen pendientes del servidor de eventos, PushKit/CallKit y audio/señalización reales de ONX. `CallKitAdapter.swift` es una frontera de integración preparada, no un servicio VoIP terminado. No se garantiza equivalencia completa con Android ni compatibilidad con todas las versiones históricas.

## Android

El código de Android 0.5 está en [android/](android/), con instrucciones de compilación y uso. Conserva nombre, imagen, diseño, sesión persistente, apertura directa y menú mediante llavecita. La APK previamente entregada está firmada con una clave local que no se publica en este repositorio.

## Pruebas y privacidad

Desde `tests/`: `npm ci` y `npm test`. Las pruebas usan DOM representativo y MessagePorts reales en Node; no ejecutan WKWebView. El éxito de esas pruebas no confirma funcionamiento nativo, recepción bloqueada ni voz.

No subas enlaces personales, contraseñas, claves APNs, certificados de firma o perfiles de aprovisionamiento. Las credenciales futuras se configuran mediante secretos de GitHub o un almacén seguro del servidor.
