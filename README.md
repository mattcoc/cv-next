# Client alternativo per ClasseViva

App Android non ufficiale per consultare il registro elettronico ClasseViva, con interfaccia Material You (Jetpack Compose, Material 3, colori dinamici).

> **Progetto non ufficiale.** Non è affiliato, sponsorizzato o approvato da Gruppo Spaggiari Parma S.p.A.
> "ClasseViva" è un marchio registrato di Gruppo Spaggiari Parma S.p.A. Tutti i dati mostrati e il servizio appartengono ai rispettivi proprietari.
> L'app usa il servizio ClasseViva con le credenziali dell'utente. Uso a proprio rischio.

## Funzioni

- Oggi: lezioni del giorno con swipe tra i giorni, ultimi voti, prossimi eventi, assenze da giustificare, pagamenti da fare
- Voti: medie per periodo (trimestre, pentamestre, quadrimestri) e per materia, dettaglio dei voti
- Agenda: eventi e compiti dei prossimi 30 giorni, filtro compiti
- Bacheca: circolari con lettore PDF integrato, zoom, download e condivisione
- Funzioni: scrutini, assenze, note, lezioni, file dei docenti, compiti, più le sezioni del sito (colloqui, sportello, PagOnline, ecc.) aperte con accesso automatico
- Notifiche configurabili: bacheca, voti, agenda, materiale dei docenti, note, pagamenti
- Più account, tema chiaro/scuro, scelta del colore

## Privacy

- Le credenziali vengono salvate **solo sul telefono**, cifrate con Android Keystore (EncryptedSharedPreferences).
- L'app comunica **esclusivamente con i server di Gruppo Spaggiari** (`web.spaggiari.eu`).
- Nessun server dello sviluppatore, nessuna statistica, nessuna pubblicità, nessun tracciamento.
- I file scaricati (PDF, allegati) restano nella cache dell'app o nella cartella Download se l'utente li salva.
- Uscendo da un account le sue credenziali vengono cancellate; disinstallando l'app viene eliminato tutto.

## Compilazione

Requisiti: Android Studio recente, JDK 17+, Android 8.0 (API 26) o superiore sul dispositivo.

```
./gradlew assembleDebug
```

L'APK si trova in `app/build/outputs/apk/debug/`.

## Note

Le API usate non sono documentate pubblicamente e possono cambiare in qualsiasi momento, rendendo l'app non funzionante.
