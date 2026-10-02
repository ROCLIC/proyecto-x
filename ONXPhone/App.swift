import SwiftUI
import WebKit
import Network
import Security
import AVFoundation
import AVKit
import UserNotifications
import AudioToolbox

enum Vault {
    static let key: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.onxphone.link", kSecAttrAccount as String: "character"]
    static func read() -> String {
        var query = key; query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?; guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    static func save(_ link: String) -> Bool {
        let data = Data(link.utf8)
        let result = SecItemUpdate(key as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if result == errSecSuccess { return true }
        guard result == errSecItemNotFound else { return false }
        var query = key; query[kSecValueData as String] = data; query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }
    static func clear() { SecItemDelete(key as CFDictionary) }
}

final class WeakBridge: NSObject, WKScriptMessageHandler {
    weak var model: PhoneModel?
    init(_ model: PhoneModel) { self.model = model }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) { model?.receive(message) }
}

final class PhoneModel: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate, UNUserNotificationCenterDelegate {
    @Published var settings = Vault.read().isEmpty
    @Published var status = "Conectando…"
    @Published var incoming = false
    @Published var caller = ""
    @Published var error = ""
    @Published var audioMenu = false
    var web: WKWebView!
    private var network = NWPathMonitor()
    private var online = true
    private var foreground = true
    private var retry: DispatchWorkItem?
    private var attempt = 0
    private var loginExpired = false
    private var media = false
    private var callID = ""
    private var ticker: Timer?
    private var player: AVAudioPlayer?
    private var pendingAnswer = ""
    private var clearing = false
    private var navigationGeneration = 0
    private let host = "game-fivem-ui-es.onx.gg"
    override init() {
        super.init()
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default(); config.allowsInlineMediaPlayback = true; config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.add(WeakBridge(self), name: "onx")
        web = WKWebView(frame: .zero, configuration: config); web.navigationDelegate = self; web.uiDelegate = self
        web.isOpaque = false; web.backgroundColor = UIColor(red: 0.047, green: 0.039, blue: 0.082, alpha: 1)
        web.allowsBackForwardNavigationGestures = true
        let notifications = UNUserNotificationCenter.current(); notifications.delegate = self
        let answer = UNNotificationAction(identifier: "answer", title: "Contestar", options: [.foreground])
        let reject = UNNotificationAction(identifier: "reject", title: "Rechazar", options: [.foreground, .destructive])
        notifications.setNotificationCategories([UNNotificationCategory(identifier: "ONX_CALL", actions: [answer, reject], intentIdentifiers: [], options: [])])
        network.pathUpdateHandler = { [weak self] path in DispatchQueue.main.async { self?.networkChanged(path.status == .satisfied) } }
        network.start(queue: DispatchQueue(label: "onx.network"))
        ticker = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            guard let self = self, self.foreground else { return }
            self.web.evaluateJavaScript("window.__onxPhonePoll && window.__onxPhonePoll()", completionHandler: nil)
            if !self.callID.isEmpty && UserDefaults.standard.object(forKey: "callVibration") as? Bool != false { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
        }
        if !Vault.read().isEmpty { load() }
    }
    deinit { network.cancel(); ticker?.invalidate(); retry?.cancel() }
    static func isPhone(_ url: URL?) -> Bool { url?.scheme == "https" && url?.host == "game-fivem-ui-es.onx.gg" && (url?.port == nil || url?.port == 443) }
    func connect(_ text: String) {
        guard !clearing else { error = "Espera a que termine el cierre de sesión."; return }
        guard let parts = URLComponents(string: text.trimmingCharacters(in: .whitespacesAndNewlines)), let url = parts.url,
            Self.isPhone(url), parts.path == "/", parts.user == nil,
            let token = parts.queryItems?.first(where: { $0.name == "_e_t" })?.value, !token.isEmpty else { error = "Pega el enlace personal completo de ONX."; return }
        guard Vault.save(url.absoluteString) else { error = "No se pudo guardar el enlace de forma segura."; return }
        askNotifications(); settings = false; loginExpired = false; attempt = 0; load()
    }
    func askNotifications() { UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in } }
    func load() {
        guard let url = URL(string: Vault.read()) else { settings = true; return }
        retry?.cancel(); retry = nil; status = online ? "Reconectando…" : "Sin internet"
        web.load(URLRequest(url: url))
    }
    func reload() { guard callID.isEmpty && !media else { error = "Espera a que termine la llamada."; return }; loginExpired = false; attempt = 0; load() }
    func networkChanged(_ value: Bool) {
        let returned = !online && value; online = value
        if !value { status = "Sin internet" }
        else if returned && foreground { reconnect() }
    }
    func reconnect() {
        guard online, foreground, !loginExpired, callID.isEmpty, !media, retry == nil, Self.isPhone(web.url) || web.url == nil else { return }
        status = "Reconectando…"; let delay = min(60.0, 5.0 * pow(2.0, Double(min(attempt, 4)))); attempt += 1
        let job = DispatchWorkItem { [weak self] in
            guard let self = self else { return }; self.retry = nil
            guard self.online, self.foreground, !self.loginExpired, self.callID.isEmpty, !self.media else { return }; self.load()
        }
        retry = job; DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: job)
    }
    func phase(_ phase: ScenePhase) {
        foreground = phase == .active
        if foreground { web.evaluateJavaScript("window.__onxPhonePoll && window.__onxPhonePoll()", completionHandler: nil) }
        else { retry?.cancel(); retry = nil; status = "iOS puede suspender la conexión" }
    }
    func back() { web.evaluateJavaScript("window.__onxPhoneBack && window.__onxPhoneBack()") { [weak self] value, _ in
        guard let self = self else { return }; if !(value as? Bool ?? false) && self.web.canGoBack && !Self.isPhone(self.web.url) { self.web.goBack() }
    } }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if clearing && navigationAction.request.url?.absoluteString == "about:blank" { decisionHandler(.allow); return }
        guard navigationAction.targetFrame?.isMainFrame != false else { decisionHandler(.allow); return }
        guard let url = navigationAction.request.url, url.scheme == "https", url.port == nil || url.port == 443,
            [host, "auth.onx.gg", "onx.gg", "steamcommunity.com", "store.steampowered.com"].contains(url.host ?? "") else { decisionHandler(.cancel); return }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url, url.scheme == "https", [host, "auth.onx.gg", "onx.gg", "steamcommunity.com", "store.steampowered.com"].contains(url.host ?? "") { web.load(navigationAction.request) }; return nil
    }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { navigationGeneration += 1; status = clearing ? "Cerrando sesión…" : Self.isPhone(web.url) ? "Conectando…" : "Inicia sesión en ONX / Steam" }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard !clearing, Self.isPhone(webView.url), let path = Bundle.main.url(forResource: "phone-bridge", withExtension: "js"), let script = try? String(contentsOf: path, encoding: .utf8),
            let transportPath = Bundle.main.url(forResource: "ios-transport", withExtension: "js"), let transport = try? String(contentsOf: transportPath, encoding: .utf8) else { return }
        let generation = navigationGeneration
        webView.evaluateJavaScript(script) { [weak self] _, _ in
            guard let self = self, self.navigationGeneration == generation, !self.clearing, Self.isPhone(self.web.url) else { return }
            self.web.evaluateJavaScript(transport, completionHandler: nil)
        }
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { if (error as NSError).code != NSURLErrorCancelled { reconnect() } }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { reconnect() }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) { stopRinging(); reconnect() }
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) { decisionHandler(origin.protocol == "https" && origin.host == host && (origin.port == 0 || origin.port == 443) ? .prompt : .deny) }
    func receive(_ message: WKScriptMessage) {
        guard !clearing, !Vault.read().isEmpty, message.frameInfo.isMainFrame, Self.isPhone(message.frameInfo.request.url), let text = message.body as? String, text.utf8.count <= 8192,
            let data = text.data(using: .utf8), let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = object["type"] as? String else { return }
        switch type {
        case "status":
            media = object["media"] as? Bool ?? false
            let value = object["value"] as? String ?? "loading"
            if value == "login" { if !loginExpired { notify(title: "ONX phone · inicia sesión", body: "Abre el celular para renovar el acceso o cambiar el enlace.", call: false, id: "") }; loginExpired = true; retry?.cancel(); retry = nil; status = "Sesión vencida · inicia sesión" }
            else if !online || value == "offline" { status = "Sin internet" }
            else if value == "ready" { status = "Conectado · web activa"; attempt = 0; loginExpired = false }
            else { status = "Conectando…" }
            if pendingAnswer == callID && !callID.isEmpty { pendingAnswer = ""; action("answer") }
        case "call":
            guard let id = object["id"] as? String, id != callID else { return }
            stopRinging(); callID = id; caller = object["name"] as? String ?? "Llamada ONX"
            if flag("calls") { incoming = true; ring(); notify(title: caller, body: "Llamada entrante", call: true, id: id) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 90) { [weak self] in
                guard let self = self, self.callID == id, self.incoming else { return }
                self.stopRinging(); self.incoming = false; self.callID = ""
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["onx-call"])
            }
        case "callEnd": if object["id"] as? String == callID { stopRinging(); incoming = false; callID = ""; pendingAnswer = ""; UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["onx-call"]) }
        case "actionResult":
            if object["id"] as? String == callID { if object["ok"] as? Bool == true { stopRinging(); incoming = false } else { error = "La llamada terminó o sus controles no están disponibles." } }
        case "notification":
            let kind = object["kind"] as? String ?? "other"
            if flag(["messages", "emails", "groups"].contains(kind) ? "messages" : "other") { notify(title: object["title"] as? String ?? "ONX phone", body: object["body"] as? String ?? "", call: false, id: "") }
        default: break
        }
    }
    func flag(_ key: String) -> Bool { UserDefaults.standard.object(forKey: key) as? Bool ?? true }
    func action(_ action: String) {
        guard !callID.isEmpty else { return }
        guard let data = try? JSONSerialization.data(withJSONObject: ["type": "action", "action": action, "id": callID]), let payload = String(data: data, encoding: .utf8),
            let literalData = try? JSONSerialization.data(withJSONObject: [payload]), let literal = String(data: literalData, encoding: .utf8) else { return }
        settings = false; web.evaluateJavaScript("window.__onxNativeAction && window.__onxNativeAction(\(literal)[0])", completionHandler: nil)
    }
    func ring() {
        if flag("callVibration") { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
        guard flag("callSound"), let path = Bundle.main.url(forResource: UserDefaults.standard.string(forKey: "ringtone") ?? "soft", withExtension: "wav") else { return }
        player = try? AVAudioPlayer(contentsOf: path); player?.numberOfLoops = -1; player?.play()
    }
    func stopRinging() { player?.stop(); player = nil }
    func notify(title: String, body: String, call: Bool, id: String) {
        let content = UNMutableNotificationContent(); content.title = title; content.body = body
        if !call && flag("messageSound") { content.sound = .default }
        if call { content.categoryIdentifier = "ONX_CALL"; content.userInfo = ["callID": id] }
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: call ? "onx-call" : UUID().uuidString, content: content, trigger: nil), withCompletionHandler: nil)
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) { completionHandler(notification.request.content.categoryIdentifier == "ONX_CALL" ? [] : [.banner, .sound]) }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { completionHandler(); return }
            if let id = response.notification.request.content.userInfo["callID"] as? String, id == self.callID {
                if response.actionIdentifier == "answer" { self.pendingAnswer = id }
                else if response.actionIdentifier == "reject" { self.action("reject") }
            }; self.settings = false; completionHandler()
        }
    }
    func speaker(_ value: Bool) { do { try AVAudioSession.sharedInstance().overrideOutputAudioPort(value ? .speaker : .none) } catch { self.error = "ONX/iOS no permitió cambiar esa salida de audio." } }
    func logout() {
        clearing = true; retry?.cancel(); retry = nil; stopRinging(); incoming = false; callID = ""; pendingAnswer = ""
        Vault.clear(); web.stopLoading(); web.loadHTMLString("", baseURL: nil); settings = true; status = "Cerrando sesión…"
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        WKWebsiteDataStore.default().removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) { [weak self] in
            DispatchQueue.main.async { self?.clearing = false; self?.status = "Sin conexión" }
        }
    }
}

struct WebPhone: UIViewRepresentable {
    let model: PhoneModel
    func makeUIView(context: Context) -> WKWebView { model.web }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
struct AudioPicker: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView { let v = AVRoutePickerView(); v.tintColor = .white; return v }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
struct PhoneView: View {
    @StateObject private var model = PhoneModel()
    @Environment(\.scenePhase) private var phase
    @State private var link = ""
    @AppStorage("calls") private var calls = true
    @AppStorage("callSound") private var callSound = true
    @AppStorage("callVibration") private var vibration = true
    @AppStorage("messages") private var messages = true
    @AppStorage("other") private var other = true
    @AppStorage("messageSound") private var messageSound = true
    @AppStorage("ringtone") private var ringtone = "soft"
    private let purple = Color(red: 0.77, green: 0.64, blue: 1)
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.16, green: 0.08, blue: 0.24), Color(red: 0.047, green: 0.039, blue: 0.082)], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack(spacing: 16) {
                    Button { model.settings.toggle() } label: { Image(systemName: "wrench") }.accessibilityLabel("Ajustes")
                    Button { model.audioMenu = true } label: { Image(systemName: "speaker.wave.2") }.accessibilityLabel("Salida de audio")
                    Text(model.status).font(.caption).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    Button { model.reload() } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Recargar")
                }.padding(14).foregroundColor(purple)
                // Retain one WebView when displaying settings; never move it out of the view tree.
                ZStack {
                    WebPhone(model: model).opacity(model.settings ? 0 : 1).allowsHitTesting(!model.settings)
                    if model.settings { menu }
                }
            }
            if model.incoming {
                VStack(spacing: 24) {
                    Image("brand_art").resizable().scaledToFit().frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 20))
                    Text("LLAMADA ENTRANTE").font(.caption).tracking(2).foregroundColor(purple)
                    Text(model.caller).font(.largeTitle).multilineTextAlignment(.center)
                    Button("Contestar") { model.action("answer") }.buttonStyle(.borderedProminent).tint(.green)
                    Button("Rechazar") { model.action("reject") }.buttonStyle(.borderedProminent).tint(.red)
                    Button("Silenciar") { model.stopRinging() }
                    credit
                }.padding(30).frame(maxWidth: .infinity, maxHeight: .infinity).background(Color(red: 0.07, green: 0.04, blue: 0.12))
            }
        }.preferredColorScheme(.dark)
        .onChange(of: phase) { model.phase($0) }
        .alert("ONX phone", isPresented: Binding(get: { !model.error.isEmpty }, set: { if !$0 { model.error = "" } })) { Button("Aceptar") { model.error = "" } } message: { Text(model.error) }
        .sheet(isPresented: $model.audioMenu) {
            VStack(spacing: 24) {
                Text("Salida de audio").font(.title2)
                Button("Altavoz") { model.speaker(true) }
                Button("Automática / auricular") { model.speaker(false) }
                AudioPicker().frame(width: 60, height: 60)
                Text("La selección de Bluetooth y otras salidas depende de iOS, del accesorio y del audio de ONX.").font(.caption).multilineTextAlignment(.center)
                Button("Cerrar") { model.audioMenu = false }
            }.padding(28).preferredColorScheme(.dark)
        }
    }
    var credit: some View { Text("ONX phone · por roclic").font(.caption2).foregroundColor(.secondary) }
    var menu: some View {
        ScrollView {
            VStack(spacing: 22) {
                Image("brand_art").resizable().scaledToFit().frame(width: 88, height: 88).clipShape(RoundedRectangle(cornerRadius: 25))
                Text("ONX phone").font(.largeTitle.weight(.medium))
                Text("Tu ciudad, siempre cerca.").font(.subheadline).foregroundColor(.secondary)
                if !Vault.read().isEmpty { Button("Abrir mi celular") { model.settings = false }.buttonStyle(.borderedProminent).tint(purple) }
                VStack(alignment: .leading, spacing: 14) {
                    Text("ENLACE PERSONAL").font(.caption).tracking(2).foregroundColor(purple)
                    TextField("Pega tu enlace de ONX", text: $link).textInputAutocapitalization(.never).disableAutocorrection(true).keyboardType(.URL).textFieldStyle(.roundedBorder)
                    Button("Guardar y conectar") { model.connect(link); link = "" }.buttonStyle(.borderedProminent).tint(purple)
                }.padding(20).background(.white.opacity(0.05)).cornerRadius(24)
                VStack(spacing: 15) {
                    Toggle("Avisar llamadas", isOn: $calls)
                    Toggle("Sonido de llamada", isOn: $callSound)
                    Toggle("Vibración de llamada", isOn: $vibration)
                    Picker("Tono", selection: $ringtone) { Text("Suave").tag("soft"); Text("Digital").tag("digital") }
                    Toggle("Mensajes y correos", isOn: $messages)
                    Toggle("Otros avisos", isOn: $other)
                    Toggle("Sonido de avisos", isOn: $messageSound)
                    Button("Permitir notificaciones") { model.askNotifications() }
                }.tint(purple).padding(20).background(.white.opacity(0.05)).cornerRadius(24)
                Text("La recepción con iPhone bloqueado requiere integrar los avisos del servidor con Apple. En esta base, las llamadas y mensajes se detectan mientras la web está activa.").font(.caption).foregroundColor(.secondary)
                Button("Cerrar sesión en este móvil", role: .destructive) { model.logout() }
                credit
            }.padding(24)
        }
    }
}
@main struct ONXPhoneApp: App { var body: some Scene { WindowGroup { PhoneView() } } }
