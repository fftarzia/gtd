# GTDdo: brand e compatibilità

La forma ufficiale del nome è **GTDdo** (maiuscole GTD, minuscole do).

La prima versione del rebranding riguarda intestazioni, loghi provvisori, PWA e testi principali, senza cambiare i nomi interni usati da API, Docker, variabili d'ambiente, database, service worker, integrazioni o pacchetti. Questi identificativi restano deliberatamente `tududi` per garantire la compatibilità con il progetto originale.

Il wordmark e l'icona SVG presenti in `public/` sono **provvisori** e potranno essere sostituiti con un'identità grafica definitiva.

Per la funzione Android **Nota rapida** si userà l'endpoint autenticato esistente `POST /api/v1/inbox` con il campo `content` e un `source` di provenienza Android. L'app Android non fa parte di questo primo commit di branding.

## Crediti

GTDdo è un fork di [tududi](https://github.com/chrisvel/tududi), distribuito con licenza MIT. Conservare il file [LICENSE](../LICENSE) e gli avvisi di copyright originali.
