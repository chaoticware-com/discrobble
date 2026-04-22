import Foundation

enum AuthProvider: String, CaseIterable, Codable, Identifiable {
    case lastfm
    case discogs

    var id: String {
        rawValue
    }

    var displayName: String {
        switch self {
        case .lastfm:
            return "Last.fm"
        case .discogs:
            return "Discogs"
        }
    }
}
