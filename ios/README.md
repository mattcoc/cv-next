# CV Next per iOS

Versione iOS di CV Next, scritta in SwiftUI con lo stile Liquid Glass di iOS 26. Ha le stesse sezioni dell'app Android (Oggi, Voti, Bacheca, Funzioni) e la stessa logica: rilevamento di verifiche e interrogazioni, "Ci sei anche tu", "Quanto devo prendere?", promemoria, notifiche.

Richiede iOS 26 (iPhone 11 o successivi).

## Compilare

La build gira su GitHub Actions (workflow **iOS**, runner macOS): produce `CVNext.ipa` non firmata e gli screenshot delle schermate con dati di esempio.

In locale, su un Mac con Xcode 26:

```sh
brew install xcodegen
cd ios && xcodegen generate && open CVNext.xcodeproj
```

## Installare senza App Store

L'IPA non è firmata: si installa con [SideStore](https://sidestore.io) o [AltStore](https://altstore.io) usando un Apple ID gratuito. La firma dura 7 giorni; SideStore la rinnova da solo.

## Differenze da Android

- Niente aggiornamento in-app: su iOS non si possono installare app scaricate.
- Le notifiche arrivano quando iOS concede un aggiornamento in background, di solito qualche volta al giorno, non ogni ora.
- Il promemoria della sera viene preparato in anticipo con i dati dell'ultimo aggiornamento.
- Gli allegati si aprono con l'anteprima di sistema (zoom, condivisione, salvataggio in File).
- Il widget dei compiti non c'è ancora.
