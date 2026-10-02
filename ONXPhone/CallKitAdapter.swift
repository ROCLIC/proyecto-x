import Foundation
import CallKit
import AVFoundation

/// Integration boundary: use only after implementing server signaling and a native
/// media engine. This class is deliberately not enabled by the WebView-only app.
final class CallKitAdapter: NSObject, CXProviderDelegate {
    struct Actions {
        let answer: (UUID, @escaping (Bool) -> Void) -> Void
        let end: (UUID, @escaping (Bool) -> Void) -> Void
        let mute: (UUID, Bool, @escaping (Bool) -> Void) -> Void
        let activateAudio: (AVAudioSession) -> Void
        let deactivateAudio: (AVAudioSession) -> Void
        let reset: () -> Void
    }
    private let provider: CXProvider
    private let actions: Actions
    private var reported = Set<UUID>()
    init(actions: Actions) {
        self.actions = actions
        let config = CXProviderConfiguration(localizedName: "ONX phone")
        config.supportsVideo = true; config.maximumCallGroups = 1; config.maximumCallsPerCallGroup = 1
        config.supportedHandleTypes = [.generic]; config.includesCallsInRecents = false
        provider = CXProvider(configuration: config)
        super.init(); provider.setDelegate(self, queue: .main)
    }
    /// Call synchronously from the genuine PushKit VoIP notification callback;
    /// always finish its completion after reportNewIncomingCall completes.
    func report(id: UUID, name: String, video: Bool = false, completion: @escaping (Error?) -> Void) {
        if reported.contains(id) { completion(nil); return }
        let update = CXCallUpdate(); update.localizedCallerName = name
        update.remoteHandle = CXHandle(type: .generic, value: name); update.hasVideo = video
        update.supportsHolding = false; update.supportsGrouping = false; update.supportsUngrouping = false; update.supportsDTMF = false
        reported.insert(id)
        provider.reportNewIncomingCall(with: id, update: update) { [weak self] error in
            DispatchQueue.main.async { if error != nil { self?.reported.remove(id) }; completion(error) }
        }
    }
    func ended(id: UUID, reason: CXCallEndedReason = .remoteEnded) { reported.remove(id); provider.reportCall(with: id, endedAt: Date(), reason: reason) }
    func providerDidReset(_ provider: CXProvider) { reported.removeAll(); actions.reset() }
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) { actions.answer(action.callUUID) { ok in DispatchQueue.main.async { if ok { action.fulfill() } else { action.fail() } } } }
    func provider(_ provider: CXProvider, perform action: CXEndCallAction) { actions.end(action.callUUID) { [weak self] ok in DispatchQueue.main.async { if ok { self?.reported.remove(action.callUUID); action.fulfill() } else { action.fail() } } } }
    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) { actions.mute(action.callUUID, action.isMuted) { ok in DispatchQueue.main.async { if ok { action.fulfill() } else { action.fail() } } } }
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) { actions.activateAudio(audioSession) }
    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) { actions.deactivateAudio(audioSession) }
    func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) { action.fail() }
}
