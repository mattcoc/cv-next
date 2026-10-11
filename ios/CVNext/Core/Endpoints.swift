import Foundation

/// Tutti gli indirizzi e gli identificativi dei servizi esterni, in un unico punto.
enum Endpoints {
    static let web = "https://web.spaggiari.eu"
    static let rest = "\(web)/rest/v1"
    static let webLogin = "\(web)/auth-p7/app/default/AuthApi4.php?a=aLoginPwd"
    static func payments(_ studentId: String) -> String { "\(web)/rest/w1/parents/\(studentId)/pagoonline/payments/?" }

    /// Intestazioni richieste dalle API dell'app ufficiale.
    static let userAgent = "CVVS/std/4.1.7 Android/10"
    static let apiKey = "Tg1NWEwNGIgIC0K"

    static let repo = "https://github.com/mattcoc/cv-next"

    /// L'anno scolastico inizia a settembre: a ottobre 2026 l'anno precedente è il 2025/26, ospitato su web25.
    static func lastYearHost(_ today: Date = Date()) -> String {
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month], from: today)
        let start = (c.month ?? 1) >= 9 ? (c.year ?? 2000) : (c.year ?? 2000) - 1
        return String(format: "https://web%02d.spaggiari.eu", (start - 1) % 100)
    }
}

/// Pagine del registro web che si aprono dentro l'app.
enum WebLinks {
    static let home = "\(Endpoints.web)/home/app/default/menu_webinfoschool_studenti.php"
    static let meetings = "\(Endpoints.web)/fml/app/default/genitori_colloqui.php"
    static let desk = "\(Endpoints.web)/fml/app/default/alunni_sportello.php"
    static let certificates = "\(Endpoints.web)/cvv/app/default/att_cert_studente.php"
    static let payments = "\(Endpoints.web)/pfo/app/default/scadenze.php"
    static let reports = "\(Endpoints.web)/sol/app/default/documenti_sol.php?quad=1"
    static let projects = "\(Endpoints.web)/pdp/app/default/lista_pfi_studente.php"
    static var lastYear: String { "\(Endpoints.web)/home/app/default/xasapi.php?a=lap&bu=\(Endpoints.lastYearHost())&ru=/home/&fu=xasapi-ERROR.php" }
}
