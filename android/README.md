# ONX phone 0.5

Aplicación Android independiente para el celular web de un jugador de ONX. No está afiliada con ONX. Cada jugador pega su enlace; la APK y el código no contienen enlaces personales ni sesiones.

## Instalar y actualizar

Instala ONX-phone-0.5.apk sobre la versión anterior, sin desinstalarla. Conserva identificador y firma para mantener el enlace cifrado y las cookies. Requiere Android 8 o posterior y Android System WebView actualizado. No se ha certificado en todas las versiones o fabricantes.

Pega tu enlace completo y pulsa Conectar mi celular. Autoriza notificaciones y micrófono; inicia sesión con Steam si ONX lo pide. La sesión se conserva mientras ONX la acepte. La APK no puede extender una sesión o enlace vencidos.

## Recepción al bloquear el móvil

Desde los ajustes de ONX phone:

1. Notificaciones y audio: permite ambos.
2. Pantalla de llamada: permite pantalla completa cuando Android lo ofrezca. Si no se permite, puede aparecer solo la notificación Contestar/Rechazar.
3. Actividad en segundo plano: busca ONX phone y permite batería sin restricciones. Los nombres de estas opciones cambian según el fabricante.
4. Recepción con pantalla bloqueada: activa el modo experimental y concede Mostrar sobre otras apps. Aloja el WebView en una ventana mínima de 1 × 1 píxel, sin controles táctiles, cuando sales de la app. Puede consumir más batería. Es opcional y se puede desactivar.
5. Conecta el celular y comprueba la notificación permanente. Probar una llamada comprueba solo el aviso nativo local; después pide una llamada real con la pantalla bloqueada durante varios minutos.

**La recepción bloqueada sigue siendo experimental y no está comprobada en un dispositivo Android.** Esta APK no usa notificaciones push de ONX: detecta tarjetas de la web mientras su conexión siga viva. Android puede suspender internet, proceso o WebView incluso con el servicio activo. El modo experimental no garantiza llamadas en todos los móviles ni después de detener la aplicación.

La fiabilidad de un teléfono normal requeriría integración de eventos/push con el servidor de ONX. Como jugador sin acceso a ese servidor, esta APK solo puede mejorar la continuidad de la web. No recibe eventos que ONX no envíe a ese celular web.

## Mejoras de 0.3

- Reconexión al volver internet y cuando falla la página o el detector deja de responder. Reintentos de 5, 10, 20, 40 y hasta 60 segundos. Evita recargar durante autenticación Steam, llamada entrante o audio detectado en elementos multimedia. El audio gestionado de otra forma puede no detectarse.
- Estado visible: Sin internet, Reconectando, Conectado · web activa, Sesión vencida · inicia sesión. Conectado confirma internet y respuesta del detector web; no confirma por sí solo que el servidor de juego envíe todos los eventos.
- Sesión vencida: avisa si la página lo indica o responde HTTP 401/403. Detiene reintentos de ese acceso. Inicia sesión o cambia el enlace; Recargar permite reintentar manualmente. Errores de subrecursos y de otras webs no se clasifican como sesión vencida.
- Llamadas y avisos: activar/desactivar llamadas, sonido y vibración de llamada, mensajes/correos, otras apps y sonido de mensajes/otros avisos. Los silenciosos usan un canal separado. Esto afecta los avisos nativos; ONX puede producir sus propios sonidos, ajustables dentro de su web.
- Elegir tono: selector de Android, incluidos predeterminado y silencio. El timbre respeta silencio y No molestar. Android 8/8.1 repite mediante el comprobador; versiones posteriores usan bucle.
- Salida de audio: botón de altavoz en la barra, con Automática, Auricular, Altavoz y Bluetooth conectado. Usa dispositivos de comunicación Android, con Bluetooth SCO y auriculares BLE disponibles en Android 12 o posterior. Desde Android 12 pide permiso de dispositivos cercanos para Bluetooth. La voz depende de cómo ONX/WebView gestione audio y necesita prueba real. Automática y detener servicio restauran el estado anterior.
- Conserva nombre, estética morada, imagen, adaptación de tamaño y navegación Atrás de 0.2.

## Uso y privacidad

El enlace se cifra mediante Android Keystore, sin copia de seguridad. Cookies y sesión quedan en este móvil. Cerrar sesión en este móvil borra enlace, cookies y almacenamiento web. Detener conexión apaga el servicio sin olvidar sesión. Después de reiniciar el teléfono, abre ONX phone para reactivar. Forzar detención impide recibir avisos.

Atrás acciona la flecha interna ONX y luego Inicio del celular. Desde el inicio del celular, envía la app a segundo plano. El menú de acceso y ajustes se abre con la llavecita de la barra. Respeta botones/gestos Android; Inicio y Recientes siguen controlados por el sistema.

Las llamadas muestran el llamante en pantalla bloqueada. Contestar abre el celular y solicita desbloqueo seguro. Los mensajes se marcan privados, aunque Android permite cambiar visibilidad. Cámara solo se solicita si la web la necesita. La app no transmite el enlace a servicios adicionales.

## Validación y límites

Comprobado previamente en la web ONX: autenticación, historial, marcador, ajustes y llamada entrante con controles contestar/rechazar. El usuario confirmó audio y acceso Steam.

Comprobado para 0.3: compilación de recursos/Java/DEX, firma, navegación Atrás, nueve tamaños/orientaciones, ausencia de avisos históricos y duplicados, llamada/fin/respuesta/rechazo con identificador, clasificación de mensajes, estados de internet/sesión y política de reintentos. El detector se prueba con DOM representativo; no son pruebas del servidor ni de una APK instalada.

**No se ha ejecutado 0.5 en un móvil ni emulador Android.** Pendientes: recepción bloqueada, apariencia nativa, continuidad de voz, altavoz/Bluetooth, tonos, permisos de fabricante y actualización instalada. Si ONX cambia diseño/textos/controles, puede requerir actualización. Pantalla completa depende de Android. Avisos que desaparezcan mientras la vista esté suspendida pueden perderse.

El servicio usa notificación permanente y bloqueo parcial de CPU: consume recursos y no evita restricciones Doze. Referencia: https://developer.android.com/training/monitoring-device-state/doze-standby

## Código y compilación

Abre esta carpeta en Android Studio con SDK 35 y JDK compatible con Android Gradle Plugin 8.9.2. Construye mediante assembleDebug o el IDE. No requiere librerías de terceros. La APK entregada usa herramientas oficiales AAPT2, javac y D8, y clave de desarrollo local fuera del código distribuido. Publicar en Google Play requeriría validar sus requisitos de permisos, servicios y llamadas.

Pruebas JavaScript: desde tests/, npm install y npm test con Node.js moderno. Prueba Java: compila src/app/rpphone/companion/ReconnectPolicy.java y tests/ReconnectPolicyTest.java en un directorio temporal; ejecuta app.rpphone.companion.ReconnectPolicyTest. Ninguna simula restricciones Android.

## Prueba real recomendada

- Actualizar sin desinstalar y comprobar acceso guardado.
- Llamada real abierta: contestar, voz en ambos sentidos, rechazo, silencio y fin desde otro jugador.
- Activar permisos de recepción bloqueada y batería; bloquear 5–10 minutos y recibir llamada/mensaje reales.
- Quitar/restaurar internet sin llamada y comprobar estado/recuperación sin bucles.
- Elegir tono, desactivar vibración y silenciar mensajes; comprobar opciones por separado.
- Probar Altavoz, Bluetooth conectado y Automática con llamada real.
- Detener conexión: comprobar que paran avisos, sonido, vibración y servicio.
## Actualización 0.4: llamada recibida pero no abre pantalla

Esta actualización se centra en el caso confirmado por el usuario: la notificación llega con el móvil bloqueado, pero no aparece la pantalla para contestar/rechazar.

- La intención de pantalla de llamada usa una tarea propia, separada de la pantalla de acceso, con apertura sobre bloqueo y encendido de pantalla. Android 8.0 usa los flags compatibles; Android 8.1 o posterior usa setShowWhenLocked/setTurnScreenOn. La pantalla permanece encendida durante la llamada entrante.
- Android 15 o posterior: se autoriza explícitamente la apertura en segundo plano al crear el PendingIntent privado de la pantalla de llamada.
- Si la pantalla no se confirma tras 1,2 segundos, se solicita su apertura directamente solo si la llamada sigue activa, el teléfono está bloqueado, la opción está activada, las notificaciones/pantalla completa/canal alto están permitidos, No molestar no restringe y existe autorización de segundo plano. En Android 10 o posterior esto requiere Mostrar sobre otras apps, una excepción documentada. Android aún puede rechazar la solicitud.
- Tocar la notificación de llamada abre sus controles, en vez de mostrar primero el inicio de ONX phone. Contestar vuelve a la tarea principal tras desbloquear si es necesario.
- Pantalla de llamada muestra comprobaciones reales: permiso de pantalla completa, prioridad del canal, notificaciones, Mostrar sobre otras apps, No molestar y último resultado de apertura. Muestra también si el último evento web fue una llamada o un aviso genérico. No guarda nombres, mensajes ni enlaces en este resultado.
- Probar con el móvil bloqueado programa una llamada local para dentro de 15 segundos. Bloquea con el botón de encendido antes de ese plazo. Dura 20 segundos y usa exactamente el mecanismo de presentación de las llamadas entrantes. No comprueba la conexión de ONX.
- Llamadas y avisos permite desactivar Abrir pantalla de llamada al bloquear.

### Prueba de 0.4

1. Actualiza sobre la APK anterior, sin desinstalar. Abre y conecta el celular.
2. En Pantalla de llamada comprueba que Pantalla completa está permitida, el canal tiene prioridad alta y No molestar está desactivado. El permiso de mostrar sobre otras apps y el de pantalla completa son distintos.
3. Pulsa Probar con el móvil bloqueado y Programar. Bloquea el teléfono en los siguientes 15 segundos.
4. Comprueba si aparecen Contestar/Rechazar sobre la pantalla bloqueada. Luego abre Pantalla de llamada y consulta Último resultado.
5. Haz una llamada real desde ONX. Si solo aparece un aviso, comprueba también Último evento web: permite saber si el detector la clasificó como llamada o como aviso genérico.

Validación de 0.4: compilación/firma y prueba de reglas de presentación (no abre sin llamada activa, con teléfono desbloqueado, con pantalla ya abierta, opción desactivada, notificaciones bloqueadas, permiso de pantalla completa denegado, canal sin prioridad alta, No molestar o sin autorización de segundo plano). También se conserva la prueba del detector y la política de reconexión. Estas comprobaciones no prueban apertura física en un móvil; no hay dispositivo o emulador conectado. El fallo observado no se considera resuelto hasta que la prueba bloqueada pase en el teléfono del usuario.

Referencias oficiales:
- https://developer.android.com/guide/components/activities/secure-bal
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/develop/ui/compose/notifications/create-notification
## Actualización 0.5: diseño, crédito y apertura directa

- Menú con identidad centrada, paleta morada más suave, tarjetas con borde fino, estado en una cápsula y espaciado más uniforme.
- Barra del celular con iconos vectoriales coherentes para llavecita, audio y recarga.
- Pantalla de llamada con avatar, botones más redondeados e iconos de contestar y colgar.
- Se sustituye el texto Versión de prueba del pie de la interfaz por el crédito discreto ONX phone · por roclic. El crédito también aparece al pie de la pantalla de llamada.
- Cuando existe un enlace guardado, abrir la aplicación inicia o reutiliza la conexión y abre directamente el celular. No exige volver al menú tras cerrar o salir de la app. La web puede solicitar iniciar sesión si ONX la ha vencido.
- La llavecita abre el menú. Atrás mantiene la navegación interna; desde el inicio del celular deja la app en segundo plano y al volver continúa en el celular.
- Instala sobre 0.4 sin desinstalar para conservar enlace y sesión. La continuidad de recepción al cerrar/forzar detención mantiene los límites anteriores.

Verificación de 0.5: compilación de recursos, Java y DEX; firma compatible; regresiones del detector, navegación interna y políticas de conexión/presentación. La estética y el ciclo de apertura deben comprobarse en un móvil: no se dispone de dispositivo o emulador conectado. Esta actualización no certifica por sí sola la recepción ni la apertura de llamadas bloqueado.