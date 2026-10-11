import Foundation
import UIKit

struct Grade: Hashable {
    var subject: String, date: String, value: String
    var decimal: Double?
    var type: String, notes: String, period: String
    var periodPos: Int
    var counts: Bool
    var id: Int = 0
}

struct Event: Hashable {
    var date: String, time: String, subject: String, author: String, notes: String
    var homework: Bool
    var id: Int = 0
    var cls: String = ""
}

struct Absence: Hashable { var date: String, type: String; var justified: Bool; var reason: String }
struct Attachment: Hashable { var name: String; var num: Int }
struct Notice: Hashable {
    var title: String, date: String
    var read: Bool
    var category: String, code: String
    var id: Int
    var attachments: [Attachment]
}
struct Lesson: Hashable { var date: String; var hour: Int, hours: Int; var subject: String, topic: String, author: String; var cls: String = "" }

struct Period: Hashable {
    var pos: Int, name: String, start: String, end: String
    var current: Bool {
        let today = Day.iso(Date())
        let s = String(start.prefix(10)), e = String(end.prefix(10))
        return s.count == 10 && e.count == 10 && today >= s && today <= e
    }
}

struct School { var name: String, city: String }
struct Payment: Hashable { var id: String, title: String, amount: String, due: String }
struct NoteItem: Hashable { var type: String, date: String, author: String, text: String; var read: Bool; var id: Int = 0 }

struct DidContent: Hashable { var id: Int, name: String, type: String, date: String }
struct DidFolder: Hashable { var name: String, date: String, contents: [DidContent] }
struct DidTeacher: Hashable { var name: String, folders: [DidFolder] }

enum Opened { case file(URL), link(String), text(String) }

struct SchoolDoc: Hashable { var hash: String, title: String }
struct SchoolReport: Hashable { var title: String, url: String }
struct Documents { var docs: [SchoolDoc], reports: [SchoolReport] }

extension Data {
    var isPdf: Bool { count > 4 && prefix(4) == Data("%PDF".utf8) }
}

extension Api {
    func grades() async throws -> [Grade] {
        try await get("/grades").list("grades").map {
            Grade(
                subject: $0.str("subjectDesc").pretty, date: $0.str("evtDate"), value: $0.str("displayValue"),
                decimal: $0.double("decimalValue"), type: $0.str("componentDesc"), notes: $0.str("notesForFamily"),
                period: $0.str("periodDesc"), periodPos: $0.int("periodPos"),
                counts: $0.str("color") != "blue" && !$0.bool("canceled"), id: $0.int("evtId")
            )
        }.sorted { $0.date > $1.date }
    }

    func agenda() async throws -> [Event] {
        let today = Day.today
        return try await get("/agenda/all/\(Day.basic(Day.add(today, -30)))/\(Day.basic(Day.add(today, 30)))").list("agenda").map {
            let begin = $0.str("evtDatetimeBegin")
            let time = $0.bool("isFullDay") ? "" : String(begin.dropFirst(11).prefix(5))
            return Event(
                date: String(begin.prefix(10)), time: time, subject: $0.str("subjectDesc").pretty, author: $0.str("authorName").pretty,
                notes: $0.str("notes"), homework: $0.str("evtCode") == "AGHW", id: $0.int("evtId"), cls: $0.str("classDesc")
            )
        }.sorted { $0.date + $0.time < $1.date + $1.time }
    }

    func absences() async throws -> [Absence] {
        try await get("/absences/details").list("events").map {
            let code = $0.str("evtCode")
            let type = code == "ABA0" ? "Assenza"
                : code.hasPrefix("ABA") ? "Assenza parziale"
                : code.hasPrefix("ABU") ? "Uscita anticipata" : "Ritardo"
            return Absence(date: $0.str("evtDate"), type: type, justified: $0.bool("isJustified"), reason: $0.str("justifReasonDesc"))
        }.sorted { $0.date > $1.date }
    }

    func notices() async throws -> [Notice] {
        try await get("/noticeboard").list("items").map {
            Notice(
                title: $0.str("cntTitle"), date: $0.str("pubDT"), read: $0.bool("readStatus"), category: $0.str("cntCategory"),
                code: $0.str("evtCode"), id: $0.int("pubId"),
                attachments: $0.list("attachments").map { Attachment(name: $0.str("fileName"), num: $0.int("attachNum", 1)) }
            )
        }.sorted { $0.date > $1.date }
    }

    /// Apre la comunicazione (e la segna come letta). Il testo arriva in HTML.
    func readNotice(_ n: Notice) async throws -> String {
        guard let item = try await post("/noticeboard/read/\(n.code)/\(n.id)/101").obj("item") else { return "" }
        let html = item.str("text")
        return await MainActor.run { htmlToText(html) }
    }

    func attachment(_ n: Notice, _ a: Attachment) async throws -> URL {
        let file = Api.downloads.appendingPathComponent("notice_\(n.id)_\(a.num).pdf")
        if let size = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > 0 { return file }
        var data = try? await bytes("/noticeboard/attach/\(n.code)/\(n.id)/\(a.num)")
        if data?.isPdf != true { data = try await bytes("/noticeboard/attach/\(n.code)/\(n.id)/101") }
        guard let data, data.isPdf else { throw ApiError("Il file non è un PDF") }
        try data.write(to: file)
        return file
    }

    func periods() async throws -> [Period] {
        var seen = Set<Int>()
        return try await get("/periods").list("periods")
            .map { Period(pos: $0.int("periodPos"), name: $0.str("periodDesc").pretty, start: $0.str("dateStart"), end: $0.str("dateEnd")) }
            .filter { !$0.name.trimmed.isEmpty && seen.insert($0.pos).inserted }
            .sorted { $0.pos < $1.pos }
    }

    func school() async throws -> School {
        let c = try await get("/card").obj("card") ?? [:]
        let name = [c.str("schName").pretty, c.str("schDedication").replacingOccurrences(of: "\"", with: "").trimmed.pretty]
            .filter { !$0.trimmed.isEmpty }.joined(separator: " ")
        return School(
            name: Rx("\\s+").replace(name, " "),
            city: [c.str("schCity").pretty, c.str("schProv").uppercased()].filter { !$0.trimmed.isEmpty }.joined(separator: " ")
        )
    }

    func payments() async throws -> [Payment] {
        let raw = try await webPage(Endpoints.payments(try await studentId()))
        guard let json = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? JSON else {
            throw ApiError("Impossibile accedere ai pagamenti online (sessione web scaduta)")
        }
        let today = Day.iso(Date())
        return json.list("items").flatMap { e in
            e.list("payPlan").filter {
                $0.int("payStatus") == 1 && $0.str("deadlineDate") >= today && ($0.double("amountPaid") ?? 0) < ($0.double("amountDue") ?? 0)
            }.map {
                Payment(id: $0.str("payId"), title: e.str("evtDesc").trimmed.lowercased().capFirst, amount: num($0.double("amountDue") ?? 0), due: $0.str("deadlineDate"))
            }
        }.sorted { $0.due < $1.due }
    }

    /// Le ore consecutive della stessa lezione vengono unite ("2-3").
    func lessons(from: Date, to: Date) async throws -> [Lesson] {
        let list = try await get("/lessons/\(Day.basic(from))/\(Day.basic(to))").list("lessons").map {
            Lesson(date: $0.str("evtDate"), hour: $0.int("evtHPos"), hours: max($0.int("evtDuration", 1), 1),
                   subject: $0.str("subjectDesc").pretty, topic: $0.str("lessonArg"), author: $0.str("authorName").pretty, cls: $0.str("classDesc"))
        }.sorted { ($0.date, $0.hour) < ($1.date, $1.hour) }
        var out: [Lesson] = []
        for l in list {
            if let p = out.last, p.date == l.date, p.subject == l.subject, p.topic == l.topic, p.hour + p.hours == l.hour {
                out[out.count - 1].hours += l.hours
            } else {
                out.append(l)
            }
        }
        return out
    }

    func notes() async throws -> [NoteItem] {
        let o = try await get("/notes/all")
        let types = [("NTCL", "Nota disciplinare"), ("NTWN", "Richiamo"), ("NTTE", "Annotazione"), ("NTST", "Sanzione disciplinare")]
        return types.flatMap { key, label in
            o.list(key).map { NoteItem(type: label, date: $0.str("evtDate"), author: $0.str("authorName").pretty, text: $0.str("evtText"), read: $0.bool("readStatus"), id: $0.int("evtId")) }
        }.sorted { $0.date > $1.date }
    }

    func didactics() async throws -> [DidTeacher] {
        try await get("/didactics").list("didacticts").map { t in
            DidTeacher(name: t.str("teacherName").pretty, folders: t.list("folders").map { f in
                DidFolder(name: f.str("folderName"), date: f.str("lastShareDT"), contents: f.list("contents").map {
                    DidContent(id: $0.int("contentId"), name: $0.str("contentName"), type: $0.str("objectType"), date: $0.str("shareDT"))
                })
            }.sorted { $0.date > $1.date })
        }.filter { !$0.folders.isEmpty }.sorted { $0.name < $1.name }
    }

    func didacticItem(_ c: DidContent) async throws -> Opened {
        let data = try await bytes("/didactics/item/\(c.id)")
        if !data.isPdf, let item = ((try? JSONSerialization.jsonObject(with: data)) as? JSON)?.obj("item") {
            if let link = item.str("link").ifBlank { return .link(link) }
            if let text = item.str("text").ifBlank { return .text(text) }
        }
        let ext = data.isPdf ? "pdf" : String((c.name.contains(".") ? (c.name.split(separator: ".").last.map(String.init) ?? "bin") : "bin").lowercased().prefix(5))
        let file = Api.downloads.appendingPathComponent("did_\(c.id).\(ext)")
        try data.write(to: file)
        return .file(file)
    }

    func documents() async throws -> Documents {
        let o = try await post("/documents")
        return Documents(
            docs: o.list("documents").map { SchoolDoc(hash: $0.str("hash"), title: $0.str("desc")) },
            reports: o.list("schoolReports").map { SchoolReport(title: $0.str("desc"), url: $0.str("viewLink")) }
        )
    }

    func document(_ d: SchoolDoc) async throws -> URL {
        let file = Api.downloads.appendingPathComponent("doc_\(d.hash).pdf")
        if let size = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > 0 { return file }
        _ = try? await post("/documents/check/\(d.hash)")
        let data = try await postBytes("/documents/read/\(d.hash)")
        guard data.isPdf else { throw ApiError("Documento non disponibile") }
        try data.write(to: file)
        return file
    }
}

@MainActor
func htmlToText(_ html: String) -> String {
    guard let data = html.data(using: .utf8),
          let s = try? NSAttributedString(data: data, options: [.documentType: NSAttributedString.DocumentType.html, .characterEncoding: String.Encoding.utf8.rawValue], documentAttributes: nil)
    else { return html }
    return s.string.trimmed
}

// MARK: medie

extension Array where Element == Double {
    var avg: Double? { isEmpty ? nil : reduce(0, +) / Double(count) }
}

extension Array where Element == Grade {
    var gradeAvg: Double? { filter(\.counts).compactMap(\.decimal).avg }
    var subjectsAvg: Double? { Dictionary(grouping: self, by: \.subject).values.compactMap { $0.gradeAvg }.avg }
}

/// Cosa serve per portare la media dei voti almeno a un obiettivo.
enum Needed { case reached, one(Double), two(Double), impossible }

private func quarterUp(_ x: Double) -> Double { (x * 4 - 1e-9).rounded(.up) / 4 }

/// Il voto minimo al prossimo compito per non scendere sotto [target].
func keepGrade(_ values: [Double], _ target: Double) -> Double {
    min(max(quarterUp(target * Double(values.count + 1) - values.reduce(0, +)), 1), 10)
}

func neededGrade(_ values: [Double], _ target: Double) -> Needed {
    let sum = values.reduce(0, +)
    if !values.isEmpty && sum / Double(values.count) >= target { return .reached }
    let one = quarterUp(target * Double(values.count + 1) - sum)
    if one <= 10 { return .one(max(one, 1)) }
    let two = quarterUp((target * Double(values.count + 2) - sum) / 2)
    return two <= 10 ? .two(two) : .impossible
}
