import SwiftUI

struct LoginView: View {
    let onBack: (() -> Void)?
    let onDone: () -> Void

    @State private var user = ""
    @State private var pass = ""
    @State private var show = false
    @State private var loading = false
    @State private var error: String?
    @State private var choices: [Student]?
    @FocusState private var focus: Field?
    @Environment(\.appTint) private var tint

    private enum Field { case user, pass }

    private var canSubmit: Bool { !loading && !user.trimmed.isEmpty && !pass.isEmpty }

    var body: some View {
        ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Spacer(minLength: onBack != nil ? 64 : 48)
                    Text("CV Next").font(.system(size: 48, weight: .bold, design: .rounded)).foregroundStyle(tint)
                    Text(onBack != nil ? "Aggiungi un altro account" : "Accedi con le stesse credenziali del registro elettronico")
                        .font(.body.weight(.medium))
                        .foregroundStyle(.subtle)
                    Spacer(minLength: 12)
                    GlassEffectContainer(spacing: 12) {
                        VStack(spacing: 12) {
                            HStack {
                                Image(systemName: "person").foregroundStyle(.subtle).frame(width: 24)
                                TextField("Codice utente o email", text: $user, prompt: Text("Codice utente o email").foregroundStyle(.subtle))
                                    .textContentType(.username)
                                    .keyboardType(.emailAddress)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .submitLabel(.next)
                                    .focused($focus, equals: .user)
                                    .onSubmit { focus = .pass }
                            }
                            .padding(16)
                            .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 18))

                            HStack {
                                Image(systemName: "key").foregroundStyle(.subtle).frame(width: 24)
                                Group {
                                    if show { TextField("Password", text: $pass, prompt: Text("Password").foregroundStyle(.subtle)) } else { SecureField("Password", text: $pass, prompt: Text("Password").foregroundStyle(.subtle)) }
                                }
                                .textContentType(.password)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .submitLabel(.go)
                                .focused($focus, equals: .pass)
                                .onSubmit(submit)
                                Button { show.toggle() } label: { Image(systemName: show ? "eye.slash" : "eye") }
                                    .foregroundStyle(.subtle)
                                    .accessibilityLabel(show ? "Nascondi password" : "Mostra password")
                            }
                            .padding(16)
                            .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 18))
                        }
                    }
                    .onChange(of: user) { error = nil }
                    .onChange(of: pass) { error = nil }

                    if let error { Text(error).foregroundStyle(.red).font(.subheadline).padding(.horizontal, 4) }

                    Button(action: submit) {
                        Group {
                            if loading { ProgressView().tint(.white) } else { Text("Accedi").font(.headline) }
                        }
                        .frame(maxWidth: .infinity, minHeight: 30)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    // Resta colorato anche quando i campi sono vuoti: grigio era poco leggibile.
                    .opacity(canSubmit || loading ? 1 : 0.6)
                    .allowsHitTesting(canSubmit)

                    VStack(spacing: 6) {
                        Label("Le credenziali restano cifrate solo sul tuo telefono", systemImage: "lock.fill")
                        Text("App non ufficiale. Non affiliata a Gruppo Spaggiari Parma S.p.A.").multilineTextAlignment(.center)
                    }
                    .font(.subheadline)
                    .foregroundStyle(.subtle)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
                }
                .padding(24)
                // Su iPad il modulo resta largo come su un telefono
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
            }
        .scrollDismissesKeyboard(.interactively)
        // Lo sfondo va dietro e non in uno ZStack: le sue macchie sono più larghe di un iPhone piccolo e allargherebbero tutto.
        .background { AppBackground() }
        .overlay(alignment: .topLeading) {
            if let onBack {
                Button(action: onBack) { Image(systemName: "chevron.left").font(.title3.weight(.semibold)).frame(width: 32, height: 32) }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .padding(.leading, 16)
                    .padding(.top, 4)
                    .accessibilityLabel("Indietro")
            }
        }
        .confirmationDialog("Scegli lo studente", isPresented: Binding(get: { choices != nil }, set: { if !$0 { choices = nil } }), titleVisibility: .visible) {
            ForEach(choices ?? [], id: \.ident) { st in
                Button(st.name.ifBlank ?? st.ident) { login(st) }
            }
            Button("Annulla", role: .cancel) { choices = nil }
        } message: {
            Text("Al tuo account sono collegati più studenti. Puoi aggiungere gli altri dopo, con \"Aggiungi account\".")
        }
    }

    private func submit() { if canSubmit { login(nil) } }

    private func login(_ student: Student?) {
        choices = nil
        Task {
            loading = true
            error = nil
            do {
                try await Api().login(uid: user.trimmed, pass: pass, student: student)
                onDone()
            } catch let e as ChoiceNeeded {
                choices = e.choices
            } catch {
                self.error = error.userText
            }
            loading = false
        }
    }
}
