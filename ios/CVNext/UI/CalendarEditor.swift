import SwiftUI
import EventKit
import EventKitUI

/// Schermata di sistema per aggiungere una verifica al calendario, già compilata come evento di tutto il giorno
/// con un avviso alle 9 del giorno prima. Non serve il permesso al calendario: salva l'utente.
struct CalendarEditor: UIViewControllerRepresentable {
    let title: String
    let date: String
    let notes: String

    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator { dismiss() } }

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = title
        event.notes = notes
        event.isAllDay = true
        let day = Day.parse(date) ?? Day.today
        event.startDate = day
        event.endDate = day
        event.addAlarm(EKAlarm(relativeOffset: -15 * 3600))
        let vc = EKEventEditViewController()
        vc.eventStore = store
        vc.event = event
        vc.editViewDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: EKEventEditViewController, context: Context) {}

    final class Coordinator: NSObject, EKEventEditViewDelegate {
        let done: () -> Void
        init(done: @escaping () -> Void) { self.done = done }

        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
            done()
        }
    }
}
