# GTDdo per Android — 0.3.1

Companion Android nativo del fork [GTDdo](https://github.com/fftarzia/gtd), ispirato a tududi e distribuito con licenza MIT. Non è un’app ufficiale di tududi, Google o Samsung.

## Cosa cambia

- Dialogo Material 3 basato sulla proposta grafica 3, con tema scuro/chiaro automatico, campo multilinea e azione principale sempre fuori dall’area scorrevole.
- Nome GTDdo, icona adattiva e livello monocromatico per le icone a tema di Android.
- Pulsante nelle Impostazioni rapide, con istruzioni per spostarlo nella prima pagina e supporto alla pressione prolungata per aprire le impostazioni.
- Destinazione nel menu Condividi per contenuti testuali e link; conserva anche il titolo, se presente, senza duplicarlo nel testo.
- Invio con testo originale, senza tagli silenziosi, e avviso prima di sostituire una bozza con una condivisione.
- Richiesta di invio conservata nel ViewModel durante la rotazione; nessun retry automatico.
- Configurazione con credenziali nascoste quando già collegata. Salvataggio e verifica in un’unica azione; vecchie impostazioni mantenute se la verifica non riesce.
- Importazione/esportazione di un backup della configurazione protetto da password.

## Versioni e compatibilità

- Android app: `versionName 0.3.1`, `versionCode 4`.
- `compileSdk` e `targetSdk`: 37, Android 17; piattaforma SDK `platforms;android-37.0`.
- `minSdk`: 26, Android 8.0. Il ramo moderno del pulsante rapido usa un `PendingIntent` su Android 14 e successivi; il metodo legacy è isolato su Android 8–13.
- Material Components 1.14.0, AppCompat e Android Views. Non è un WebView né una conversione in pagina web.
- Java 21 per la compilazione; compatibilità dei sorgenti Java 17, AGP 9.1.1 e Gradle Wrapper 9.3.1 con checksum.

La verifica automatica copre le API 35 e 37 per gli ingressi Android; gli screenshot usano Android 17 e rendering nativo Skia in Robolectric. Non sostituisce il test su Samsung/One UI. Non si può garantire il comportamento di versioni Android future senza provarle.

## Prima prova senza perdere l’app funzionante

La variante `debug` mantiene l’identità dell’installazione 0.3.0 già configurata:

| Variante | Nome installazione | Application ID |
|---|---|---|
| debug | GTDdo | `com.fftarzia.gtddo.quickcapture.preview` |
| release | GTDdo | `com.fftarzia.gtddo.quickcapture` |

La 0.3.1 aggiorna direttamente la 0.3.0 compilata in questo ambiente: stesso application ID, stessa chiave di firma e stesse preferenze cifrate. Il nome visualizzato è GTDdo sia nel launcher sia nel menu Condividi e nel pannello rapido. Non disinstallare la 0.3.0 prima dell’aggiornamento. L’identificativo interno `.preview` resta invariato per conservare la configurazione; non compare nel nome dell’app.

**Non disinstallare l’app funzionante prima di verificare la firma del nuovo APK.** Il vecchio workflow generava una chiave debug temporanea senza conservarla. La continuità di firma fra 0.3.0 e 0.3.1 è stata verificata per gli APK compilati nello stesso ambiente locale; non si estende ai nuovi runner GitHub Actions. Un aggiornamento con lo stesso application ID richiede la stessa chiave di firma o una lineage autorizzata da quella chiave; un APK esistente non permette di ricostruirne la chiave privata.

## Configurazione

1. Inserire l’URL HTTPS radice del server e un token API personale GTDdo. Non usare l’URL con `/api/v1/inbox`.
2. Abilitare Cloudflare Access solo se il server lo richiede, e inserire Client ID e Client Secret del service token autorizzato dalla policy `Service Auth`.
3. Toccare **Salva e verifica**. La verifica è una richiesta GET: non crea note. Il server deve restituire una risposta JSON dell’API, non una pagina HTML di accesso.
4. Dopo il primo collegamento, usare **Esporta backup cifrato**. In futuro basta importare il file e inserire la sua password.

La configurazione iniziale manuale richiede ancora i token delle due autenticazioni già esistenti. Non sono stati eliminati controlli Cloudflare, inseriti segreti nell’APK o aggiunti endpoint di abbinamento al backend. Il trasferimento tramite file semplifica le configurazioni successive.

I campi vuoti conservano le credenziali salvate quando il server è lo stesso. Cambiando server, occorre inserire esplicitamente le nuove credenziali. L’intera configurazione viene cifrata prima di un unico commit delle preferenze; non si mescolano URL e chiavi appartenenti a configurazioni diverse.

## Backup cifrato

Il file `.gtddo` contiene solo URL, token API e coppia Cloudflare. Le note sono escluse.

- AES-256-GCM con salt casuale da 16 byte, nonce casuale da 12 byte e autenticazione dell’intestazione del formato.
- Password da 12 a 256 caratteri, derivata con PBKDF2-HMAC-SHA256 a 600.000 iterazioni, fuori dal thread dell’interfaccia.
- Password non salvata nelle preferenze, nei Bundle o nei file.
- Lettura limitata a 40 KiB, con rifiuto di file troppo grandi, versioni sconosciute, password errate o contenuti alterati.
- Prima dell’importazione sono mostrati il dominio e l’uso di Cloudflare. Nessuna richiesta al nuovo server parte prima della conferma dell’utente; le vecchie impostazioni restano valide fino a verifica riuscita.

Il file resta una copia delle credenziali, protetta dalla password scelta: conservarlo e gestirlo come tale. Le credenziali nell’app restano cifrate con Android Keystore e i backup automatici Android sono disabilitati. I nomi delle preferenze e l’alias Keystore della versione precedente sono mantenuti nella variante release.

## Pulsante accanto a Wi-Fi e torcia

La sezione si chiama **Impostazioni rapide / Quick panel**. L’app fornisce un vero `TileService`: non una notifica permanente o un comando sovrapposto al sistema.

Aprire il pannello con Wi-Fi e torcia, scegliere **Modifica** (matita), cercare GTDdo e trascinarlo nella prima pagina. Dalla 0.3.1 il nome del pulsante è **GTDdo**. Sui Samsung con pannelli separati, scorrere dall’angolo superiore destro.

Android richiede l’intervento dell’utente per aggiunta e ordinamento dei pulsanti. Posizione, dimensioni, forma rotonda e colori del pannello sono gestiti da Android/One UI, non dall’app. Non è richiesta alcuna autorizzazione speciale per modificare il sistema.

Riferimenti: [Google Quick Settings](https://developer.android.com/develop/ui/views/quicksettings-tiles), [Samsung Quick panel](https://www.samsung.com/us/support/answer/ANS10002874/).

## Condividi e invio

Riceve `ACTION_SEND` con MIME testuale ed `EXTRA_TEXT`, inclusi testi formattati ricevuti come `CharSequence`. Eventuale `EXTRA_SUBJECT` è incluso se non già presente. Non implementa upload di immagini o allegati e non si presenta come destinatario di MIME immagine.

Usa `POST /api/v1/inbox`, `Authorization: Bearer …` e, se configurati, `CF-Access-Client-Id` e `CF-Access-Client-Secret`. Endpoint e formato restano compatibili con il backend esistente. Non occorrono modifiche a Docker, PostgreSQL, Cloudflare o allo stack Portainer.

Se l’invio non riesce, il testo resta nella bozza cifrata. In caso di timeout controllare la Inbox prima di riprovare: il server potrebbe aver accettato la nota senza restituire la risposta. Nessun meccanismo client può deduplicare in modo certo questo caso senza un contratto di idempotenza sul server.

## Compilazione e verifiche

Installare SDK Platform 37.0 e Build Tools 36.0.0 con Android CLI aggiornato, quindi usare Java 21 e il wrapper incluso.

Su Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Su Linux/macOS:

```sh
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Screenshot nativi: `app/build/reports/native-screenshots/`.
- Test: `app/build/reports/tests/testDebugUnitTest/`.
- Lint: `app/build/reports/lint-results-debug.html`.
- Confronto grafico: `design-review/` e `design-qa.md`.

Il workflow GitHub Actions usa gli stessi strumenti e produce un APK debug, screenshot e rapporti. L’APK del runner usa una chiave debug temporanea: non è un aggiornamento firmato con la chiave degli APK locali già installati. Per gli aggiornamenti del dispositivo serve riutilizzare privatamente la chiave corretta, senza inserirla nel repository.

## Firma dei rilasci

La compilazione `release` rimane **non firmata** se non vengono forniti tutti i dati della propria chiave privata. Non distribuire l’APK unsigned come installabile.

Per una firma persistente, fornire privatamente all’ambiente di compilazione:

- `GTDDO_KEYSTORE_FILE`
- `GTDDO_KEYSTORE_PASSWORD`
- `GTDDO_KEY_ALIAS`
- `GTDDO_KEY_PASSWORD`

Conservare la chiave e una sua copia di sicurezza fuori dal repository, con accesso protetto. Il workflow di anteprima non crea automaticamente un’identità di firma stabile fra runner diversi. Non promettere aggiornamenti della vecchia 0.2.0 senza aver risolto prima la firma e pianificato il trasferimento della configurazione.

## Licenze

Licenza MIT dell’app: `LICENSE`. Le icone ufficiali Google Material sono attribuite in `NOTICE`; la loro licenza Apache 2.0 è inclusa nell’APK in `assets/licenses/`. La licenza dell’upstream tududi nel repository è mantenuta.
