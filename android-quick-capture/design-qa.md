# Verifica grafica GTDdo Android 0.3.1

**final result: passed**

## Riferimento ed evidenze

- Fonte visiva: `design-review/reference-option-3.png`, terza immagine scelta dall’utente, 852 × 1846 pixel.
- Implementazione: Android Views con Material 3; screenshot ottenuti da Robolectric API 37 con Skia nativo. Non sono screenshot di una pagina web o di un mock disegnato.
- Schermata principale: `design-review/capture-dark.png`, 390 × 844 px, mdpi, fontScale 1.0.
- Stato: configurazione presente con soli valori fittizi per i test; testo «Chiamare il commercialista per i documenti.»; tastiera non disegnata nel confronto; nessuna richiesta a servizi reali.
- Normalizzazione: riduzione del riferimento a 390 × 844 px, senza cornice di dispositivo. Android è renderizzato a 1 pixel per dp. Non sono presenti screenshot del Samsung reale.
- Confronto completo nello stesso input: `design-review/comparison-final.png`.
- Confronto ravvicinato nello stesso input: `design-review/comparison-detail.png`, crop allineato dell’area del dialogo con icona, titolo, editor e azioni.
- Altri stati: `capture-light.png`, `capture-large-text.png` (fontScale 1.5), `capture-narrow.png` (320 × 480 px, pannello limitato a 320 px per simulare lo spazio residuo della tastiera).

## Risultato della revisione

Non restano difetti P0/P1/P2 nell’ambito grafico verificato. Il dialogo conserva la gerarchia della proposta 3: marca, titolo, campo multilinea, annullamento e salvataggio. È un adattamento con componenti nativi Google, non una riproduzione raster della proposta.

### Tipografia

Roboto/sans-serif Android, corpo 18sp e titolo 28sp; azione principale 18sp. Nessun testo essenziale troncato con fontScale 1.5: le azioni diventano verticali. Il testo di input non viene ritagliato a una lunghezza massima silenziosa. La casella di scrittura non ha una seconda etichetta flottante quando contiene testo.

### Spaziatura e layout

Dialogo con angoli 28dp, margini esterni 16dp e spazi interni Material. La larghezza è limitata a 560dp. L’azione principale è separata dalla parte scorrevole e rimane nel pannello con lo spazio verticale ridotto. In quello stato è previsto che titolo e intestazione possano scorrere, per dare spazio al punto di scrittura. I figli sono ritagliati sul contorno arrotondato.

### Colori

Scuro: sfondo `#15121C`, dialogo `#2A2635`, editor `#454052`, testo `#F0EBF7`, azione `#CDC0FF` con testo `#302653`. Chiaro: palette indaco equivalente. Non sono riprodotte le sfumature decorative dell’immagine generata: i componenti Material usano superfici tonali uniformi. Contrasto e dimensioni di tocco sono coerenti con i componenti nativi.

### Qualità degli asset

Icona task/check e chiusura da Google Material Design Icons, come vettori nativi. L’icona usa forma adattiva e livello monocromatico nei launcher compatibili; nel pannello rapido sarà il sistema a definirne sfondo, forma e tinta. Il piccolo glifo differisce dal documento con angolo piegato del mock: scelta intenzionale per usare un’icona Google standard, riconoscibile a dimensioni ridotte.

### Testi e contenuto

Nome applicazione GTDdo; azione «Salva nella Inbox»; chiusura con descrizione accessibile e conservazione della bozza. Dalla versione 0.3.1 il nome è «GTDdo» anche nel launcher, nel menu Condividi e nel pannello rapido. L’identificativo interno è conservato per consentire l’aggiornamento della 0.3.0 con la stessa firma. Nessun token reale nelle immagini.

## Storia della correzione

1. Prima revisione nativa: con pannello stretto e spazio ridotto, l’intero dialogo scorreva e il salvataggio poteva finire sotto l’area visibile. **P2**.
2. Correzione: introdotto `CapturePanel`, corpo scorrevole separato da `CaptureActions` e impilamento delle azioni su schermi stretti/caratteri grandi. Conservata la bozza nel ViewModel durante la rotazione. Eliminata l’etichetta flottante nell’editor.
3. Seconda revisione: screenshot nativo `capture-narrow.png` mostra l’editor scorrevole e «Salva nella Inbox» nella parte fissa. Il test verifica anche limiti geometrici dell’azione. Nessuna sovrapposizione con il contenuto.
4. Rifinitura finale: label principale 18sp e clipping sul contorno del pannello. Screenshot rigenerati e confrontati con il riferimento nella composizione finale.

## Verifiche funzionali e limiti

Comando locale completato: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`.

32 test riusciti, inclusi condivisione/avvio su API 35 e 37, rendering nativo API 37, HTTPS locale con certificato fidato solo nei test, autenticazione a due livelli, redirect rifiutati, rilevamento pagina HTML Cloudflare, conservazione del testo e backup cifrato con password errata/manomissione. Lint non ha errori bloccanti; restano avvisi su versioni di librerie e risorse compatibili con SDK precedenti.

Il 24 novembre 2026 l’utente ha confermato configurazione e funzionamento della 0.3.0 sul Samsung. La 0.3.1 rimuove i suffissi dal nome visualizzato; continuità di application ID e firma verificata per gli APK locali. Non sono disponibili screenshot o misure del dispositivo reale; la conferma dell’utente non costituisce una verifica strumentale di ciascun comportamento One UI. Le API di sistema non permettono all’app di scegliere autonomamente la posizione del pulsante.

## Rifiniture facoltative

- Piccole differenze nei pesi ottici e nelle spaziature rispetto all’immagine generata sono accettate per mantenere componenti Material nativi e bersagli di tocco da almeno 48dp.
- Valutare il comportamento della tastiera Samsung e della dettatura durante la prova reale, oltre al tema del dispositivo.

## Checklist completata

- [x] Confronto di riferimento e implementazione nello stesso input.
- [x] Tipografia, spaziatura, colori, asset e testi valutati.
- [x] Tema chiaro, scuro, testo grande e finestra ridotta verificati.
- [x] Corretto il difetto P2 del salvataggio fuori vista.
- [x] Build e verifiche Android completate.
- [x] Configurazione e funzionamento della 0.3.0 sul Samsung confermati dall’utente.
- [x] Nome GTDdo e compatibilità di aggiornamento locale verificati per la 0.3.1.
