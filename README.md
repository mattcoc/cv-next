# CV Next

Client alternativo per ClasseViva.

App Android non ufficiale per consultare il registro elettronico ClasseViva, con interfaccia Material You (Jetpack Compose, Material 3, colori dinamici).

> **Progetto non ufficiale.** Non è affiliato, sponsorizzato o approvato da Gruppo Spaggiari Parma S.p.A.
> "ClasseViva" è un marchio registrato di Gruppo Spaggiari Parma S.p.A. Tutti i dati mostrati e il servizio appartengono ai rispettivi proprietari.
> L'app usa il servizio ClasseViva con le credenziali dell'utente. Uso a proprio rischio.

## Funzioni

### Oggi
La schermata iniziale riassume la giornata:
- saluto con data, classe e nome della scuola
- avviso degli **eventi da giustificare** (assenze, ritardi, uscite), con collegamento all'elenco
- avviso dei **pagamenti da fare** su PagOnline, con importo e scadenza
- **lezioni** degli ultimi 14 giorni: si scorre tra i giorni con uno swipe o dalla striscia dei giorni; per ogni lezione ci sono ora, materia, docente e argomento
- **ultimi 10 voti** in un carosello
- i prossimi 4 eventi in agenda

### Voti
- media generale con anello colorato (verde se sufficiente, rosso se insufficiente)
- schede per ogni periodo (trimestre, pentamestre, quadrimestri) più l'intero anno, con media e numero di voti; si apre in automatico il periodo in corso
- vista **per materia**, con la media e gli ultimi voti di ciascuna, oppure **recenti**, in ordine di data
- dettaglio di una materia: media dell'anno, media per periodo, tutti i voti con tipo (scritto, orale, …) e commento del docente
- i voti che non fanno media (in blu sul registro) o annullati sono mostrati a parte e non entrano nel calcolo
- **Quanto devo prendere?**: nel dettaglio della materia, il voto che serve al prossimo compito per arrivare alla media scelta (6, 7, 8 o 9) nel periodo in corso

### Agenda
- eventi e compiti dei prossimi 30 giorni, giorno per giorno con swipe, più l'elenco dei giorni successivi
- si può tornare indietro fino a 30 giorni
- filtro **Compiti** che mostra i compiti in arrivo raggruppati per data e, sotto, quelli passati

### Bacheca
- circolari e comunicazioni, con quelle non lette in evidenza e filtro per categoria
- testo della comunicazione e **lettore PDF integrato** per gli allegati: anteprima delle pagine, lettura a schermo intero con zoom, passaggio rapido tra più allegati
- **download** nella cartella Download e **condivisione** dei PDF
- aprendo una comunicazione viene segnata come letta

### Funzioni
Sezione con tutte le altre funzioni del registro.

Dall'app:
- **Scrutini**: esito degli scrutini, pagelle e documenti della scuola (i PDF si aprono nel lettore integrato)
- **Assenze**: totale di assenze, ritardi e uscite anticipate, elenco per mese con stato della giustificazione e motivo
- **Note**: note disciplinari, richiami, annotazioni e sanzioni, con data e docente
- **Lezioni**: registro delle lezioni degli ultimi 30 giorni con argomenti svolti
- **File dei docenti**: materiale didattico diviso per docente e cartella (PDF, file, link e testi)
- scorciatoie a Valutazioni, Bacheca, Agenda e Compiti

Servizi web, aperti dentro l'app **con accesso automatico** (senza reinserire la password):
- Colloqui, Sportello, PagOnline, Progettiamo la Scuola, Certificazioni, Registro Web completo, Anno precedente

Su tutte le schermate si aggiorna trascinando verso il basso.

### Notifiche
Si attivano singolarmente dalle impostazioni:
- nuove comunicazioni in **bacheca**
- nuovi **voti**
- nuovi compiti ed eventi in **agenda**
- nuovo **materiale dei docenti**
- nuove **note**
- **pagamenti** da effettuare

L'app controlla le novità di **tutti gli account salvati** circa una volta all'ora (solo con connessione attiva). Al primo controllo registra la situazione attuale, poi avvisa solo delle cose nuove. Toccando una notifica si apre la sezione corrispondente. C'è anche il pulsante "Controlla adesso".

### Account e personalizzazione
- **più account** (ad esempio più figli o genitore e studente), con passaggio rapido dal menu del profilo
- se le credenziali sono collegate a più studenti, al login si sceglie quale; gli altri si aggiungono con "Aggiungi account"
- tema **sistema, chiaro o scuro**
- colori **Material You** presi dallo sfondo (Android 12+) oppure uno tra 9 colori: indaco, blu, turchese, verde, giallo, arancio, rosso, rosa, viola
- impostazioni sempre a portata di mano con l'icona a forma di ingranaggio in alto, accanto al profilo

### Widget
Widget per la schermata home con i prossimi compiti dell'account attivo. Si aggiorna insieme al controllo delle notifiche e, toccandolo, apre l'agenda.

### Offline
L'app salva una copia dell'ultima risposta di ogni sezione: all'apertura mostra subito i dati salvati e poi li aggiorna. Senza connessione si possono consultare voti, agenda, lezioni, assenze, note e bacheca dell'ultimo aggiornamento.

### Aggiornamenti
Dalle impostazioni, "Controlla aggiornamenti" cerca l'ultima versione pubblicata nelle [Releases](../../releases) di GitHub. All'avvio l'app lo controlla da sola una volta al giorno e mostra un avviso nella schermata Oggi. Prima di scaricare si vedono le novità della versione. Se ce n'è una più nuova la scarica e la installa direttamente dall'app (la prima volta Android chiede di consentire a CV Next l'installazione di app).

## Privacy

- Le credenziali vengono salvate **solo sul telefono**, cifrate con una chiave AES dell'Android Keystore. Servono per rinnovare la sessione in automatico e per l'accesso ai servizi web.
- L'app comunica con i server di Gruppo Spaggiari (`web.spaggiari.eu`) e, solo quando controlli gli aggiornamenti, con GitHub (`api.github.com`, `github.com`) per scaricare la nuova versione. A GitHub non viene inviato nessun dato personale.
- Nessun server dello sviluppatore, nessuna statistica, nessuna pubblicità, nessun tracciamento.
- I file scaricati (PDF, allegati) restano nella cache dell'app o nella cartella Download se l'utente li salva.
- La copia dei dati per l'uso offline resta nella memoria privata dell'app e non viene inclusa nei backup.
- Uscendo da un account vengono cancellati le sue credenziali e i suoi dati salvati; disinstallando l'app viene eliminato tutto.

## Installazione

Scarica l'APK dalla pagina [Releases](../../releases) e aprilo sul telefono (bisogna consentire l'installazione da origini sconosciute). Serve Android 8.0 o superiore.

Permessi usati: **Internet**, **Notifiche** (chieste solo quando le attivi) e **Installazione di app**, usato solo per installare gli aggiornamenti scaricati da GitHub.

## Compilazione

Requisiti: Android Studio recente, JDK 17+, Android 8.0 (API 26) o superiore sul dispositivo.

```
./gradlew assembleDebug        # versione di sviluppo
./gradlew testDebugUnitTest    # test
```

L'APK si trova in `app/build/outputs/apk/debug/`.

Le release vengono compilate da GitHub Actions (**Actions → Release APK → Run workflow**, indicando la versione): il workflow esegue i test, compila la versione release, la firma con la chiave salvata nei Secrets e pubblica l'APK con le note di versione generate dai commit.

## Note

Le API usate non sono documentate pubblicamente e possono cambiare in qualsiasi momento, rendendo l'app non funzionante.
