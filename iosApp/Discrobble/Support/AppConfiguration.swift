import Foundation

enum AppConfiguration {
    static let workerBaseURL: URL = {
        let configuredURL = Bundle.main.object(forInfoDictionaryKey: "DISCROBBLE_WORKER_BASE_URL") as? String
#if DEBUG
        let allowLoopbackHTTP = true
#else
        let allowLoopbackHTTP = false
#endif

        if
            let configuredURL,
            let url = validatedWorkerBaseURL(
                from: configuredURL,
                allowLoopbackHTTP: allowLoopbackHTTP
            )
        {
            return url
        }

#if DEBUG
        return URL(string: "http://127.0.0.1:8787")!
#else
        preconditionFailure(
            "DISCROBBLE_WORKER_BASE_URL must be configured as an absolute https URL outside debug builds."
        )
#endif
    }()

    private static func validatedWorkerBaseURL(
        from rawValue: String,
        allowLoopbackHTTP: Bool
    ) -> URL? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)

        guard
            !trimmed.isEmpty,
            let url = URL(string: trimmed),
            let scheme = url.scheme?.lowercased(),
            let host = url.host?.lowercased()
        else {
            return nil
        }

        if scheme == "https" {
            return url
        }

        if allowLoopbackHTTP, scheme == "http", isLoopbackHost(host) {
            return url
        }

        return nil
    }

    private static func isLoopbackHost(_ host: String) -> Bool {
        host == "localhost" || host == "127.0.0.1" || host == "::1"
    }
}
