import AVFAudio
import Foundation
import ShazamKit

enum ShazamMicrophonePermissionStatus: String {
    case denied
    case granted
    case unavailable
    case undetermined

    var displayName: String {
        switch self {
        case .denied:
            return "Denied"
        case .granted:
            return "Granted"
        case .unavailable:
            return "Unavailable"
        case .undetermined:
            return "Undetermined"
        }
    }
}

enum ShazamRecognitionState: Equatable {
    case idle
    case requestingPermission
    case preparing
    case listening
    case matched
    case noMatch
    case failed(String)
    case unavailable(String)

    var isActive: Bool {
        switch self {
        case .requestingPermission, .preparing, .listening:
            return true
        case .idle, .matched, .noMatch, .failed, .unavailable:
            return false
        }
    }

    var statusLine: String {
        switch self {
        case .idle:
            return "Ready to capture one ShazamKit match attempt."
        case .requestingPermission:
            return "Requesting microphone permission."
        case .preparing:
            return "Preparing the managed ShazamKit session."
        case .listening:
            return "Listening for a candidate match from the microphone."
        case .matched:
            return "Received a ranked ShazamKit match result."
        case .noMatch:
            return "ShazamKit completed without a match."
        case let .failed(message), let .unavailable(message):
            return message
        }
    }
}

struct ShazamRecognitionCandidate: Identifiable {
    let id = UUID()
    let appleMusicID: String?
    let artist: String?
    let artworkURL: URL?
    let confidence: Float?
    let frequencySkew: Float
    let genres: [String]
    let matchOffsetSeconds: TimeInterval
    let predictedCurrentMatchOffsetSeconds: TimeInterval
    let rank: Int
    let shazamID: String?
    let subtitle: String?
    let title: String
    let webURL: URL?
}

struct ShazamRecognitionSnapshot {
    let candidates: [ShazamRecognitionCandidate]
    let capturedAt: Date
}

@MainActor
final class ShazamRecognitionCoordinator: ObservableObject {
    @Published private(set) var lastMatch: ShazamRecognitionSnapshot?
    @Published private(set) var lastNoMatchAt: Date?
    @Published private(set) var permissionStatus: ShazamMicrophonePermissionStatus
    @Published private(set) var recognitionState: ShazamRecognitionState = .idle

    private var cancelActiveSession: (() -> Void)?
    private var recognitionTask: Task<Void, Never>?

    init() {
        permissionStatus = Self.currentPermissionStatus()
    }

    func clearLastMatch() {
        lastMatch = nil
        lastNoMatchAt = nil
    }

    func refreshPermissionStatus() {
        permissionStatus = Self.currentPermissionStatus()
    }

    func startRecognition() {
        cancelRecognition(resetToIdle: false)
        recognitionTask = Task { [weak self] in
            await self?.runRecognition()
        }
    }

    func cancelRecognition(resetToIdle: Bool = true) {
        recognitionTask?.cancel()
        recognitionTask = nil
        cancelActiveSession?()
        cancelActiveSession = nil

        if resetToIdle {
            recognitionState = .idle
        }
    }

    private func runRecognition() async {
        guard #available(iOS 17.0, *) else {
            recognitionState = .unavailable("The iPhone ShazamKit spike requires iOS 17 or later.")
            return
        }

        recognitionState = .requestingPermission
        let granted = await requestMicrophonePermission()

        guard !Task.isCancelled else {
            return
        }

        permissionStatus = Self.currentPermissionStatus()

        guard granted else {
            recognitionState = .failed("Microphone permission is required before the iPhone ShazamKit spike can listen.")
            return
        }

        let session = SHManagedSession()
        cancelActiveSession = {
            session.cancel()
        }
        recognitionState = .preparing
        await session.prepare()

        guard !Task.isCancelled else {
            return
        }

        recognitionState = .listening
        let result = await session.result()

        guard !Task.isCancelled else {
            return
        }

        recognitionTask = nil
        cancelActiveSession = nil

        switch result {
        case let .match(match):
            lastMatch = Self.snapshot(from: match)
            lastNoMatchAt = nil
            recognitionState = .matched
        case .noMatch:
            lastNoMatchAt = Date()
            recognitionState = .noMatch
        case let .error(error, _):
            recognitionState = .failed(error.localizedDescription)
        }
    }

    private func requestMicrophonePermission() async -> Bool {
        switch Self.currentPermissionStatus() {
        case .granted:
            return true
        case .denied, .unavailable:
            return false
        case .undetermined:
            return await withCheckedContinuation { continuation in
                AVAudioApplication.requestRecordPermission { granted in
                    continuation.resume(returning: granted)
                }
            }
        }
    }

    private static func currentPermissionStatus() -> ShazamMicrophonePermissionStatus {
        switch AVAudioApplication.shared.recordPermission {
        case .denied:
            return .denied
        case .granted:
            return .granted
        case .undetermined:
            return .undetermined
        @unknown default:
            return .unavailable
        }
    }

    private static func snapshot(from match: SHMatch) -> ShazamRecognitionSnapshot {
        let candidates = match.mediaItems.enumerated().map { index, mediaItem in
            ShazamRecognitionCandidate(
                appleMusicID: mediaItem.appleMusicID,
                artist: mediaItem.artist,
                artworkURL: mediaItem.artworkURL,
                confidence: confidence(for: mediaItem),
                frequencySkew: mediaItem.frequencySkew,
                genres: mediaItem.genres,
                matchOffsetSeconds: mediaItem.matchOffset,
                predictedCurrentMatchOffsetSeconds: mediaItem.predictedCurrentMatchOffset,
                rank: index + 1,
                shazamID: mediaItem.shazamID,
                subtitle: mediaItem.subtitle,
                title: mediaItem.title ?? "Unknown title",
                webURL: mediaItem.webURL
            )
        }

        return ShazamRecognitionSnapshot(
            candidates: candidates,
            capturedAt: Date()
        )
    }

    private static func confidence(for mediaItem: SHMatchedMediaItem) -> Float? {
        if #available(iOS 18.4, *) {
            return mediaItem.confidence
        }

        return nil
    }
}
