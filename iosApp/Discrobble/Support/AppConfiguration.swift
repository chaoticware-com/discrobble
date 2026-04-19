import Foundation

enum AppConfiguration {
    static let workerBaseURL: URL = {
        let configuredURL = Bundle.main.object(forInfoDictionaryKey: "DISCROBBLE_WORKER_BASE_URL") as? String

        if
            let configuredURL,
            !configuredURL.isEmpty,
            let url = URL(string: configuredURL)
        {
            return url
        }

        return URL(string: "http://127.0.0.1:8787")!
    }()
}
