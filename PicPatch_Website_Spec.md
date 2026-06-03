# PROMPT ARCHITETTURALE PER IA: GENERATORE WEB "PICPATCH"

## Contesto per l'IA Sviluppatrice
Sei un Senior Full-Stack Developer esperto in **Next.js 14+ (App Router)**, **Tailwind CSS**, **Node.js**, e interfacciamento con la **GitHub REST API (Octokit)**.
Il tuo compito è sviluppare un'applicazione web (SaaS) che permetta agli utenti di personalizzare e generare una build automatica di un'app Android (chiamata "PicPatch").
L'app Android sorgente è già esistente e ospitata su un repository GitHub privato. Il sito web fungerà da interfaccia user-friendly per raccogliere i dati, iniettarli nel repository tramite una branch dedicata e triggerare una GitHub Action che compilerà l'APK.

---

## 1. Stack Tecnologico Richiesto
*   **Framework:** Next.js 14+ (App Router, Server Actions).
*   **Styling:** Tailwind CSS + shadcn/ui (per componenti UI accessibili, puliti e moderni).
*   **Gestione Stato Form:** React Hook Form + Zod (per validazione rigorosa).
*   **Storage Temporaneo (Frontend):** Zustand o Context API per mantenere i dati del form multi-step prima dell'invio.
*   **Integrazione GitHub:** libreria `@octokit/rest`.
*   **Email Provider:** Resend o SendGrid.
*   **Cron Jobs:** Vercel Cron (o equivalente) per le operazioni di pulizia programmata.

---

## 2. Struttura dei Dati dell'App Android (Target)
L'app Android legge i dati dinamici esclusivamente dalla cartella `app/src/main/assets/`. Il backend in Next.js dovrà sovrascrivere file specifici all'interno di questa cartella.

### 2.1 File Immagini (Da caricare)
L'utente può caricare un numero variabile di immagini (scelto da lui nel frontend), che dovranno essere salvate (e preferibilmente ridimensionate/compresse in JPG lato client/server per non pesare troppo) nei seguenti percorsi sequenziali sul repository:
*   `app/src/main/assets/cards/card_01/photo.jpg`
*   `app/src/main/assets/cards/card_02/photo.jpg`
*   ... fino a `card_N/photo.jpg`
*   **ATTENZIONE:** Se l'utente decide di inserire card di tipo Realtà Aumentata (IMAGE_MARKER o HYBRID), queste richiederanno anche un'immagine marker associata. Devono essere salvate come: `app/src/main/assets/cards/card_N/marker.jpg`.

### 2.2 File Configurazione: `cards_config.json`
Il backend dovrà generare un file JSON e sovrascriverlo nel path: `app/src/main/assets/cards_config.json`. L'array "cards" deve essere lungo esattamente quanto il numero di card configurate.
Lo schema esatto di un elemento JSON atteso dall'app Android è il seguente (Zod Schema di riferimento):
```json
{
  "cards": [
    {
      "id": 1, // Progressivo da 1 a N
      "type": "TIMER",
      "title": "String",
      "dedication": "String",
      "photoPath": "cards/card_01/photo.jpg"
    },
    {
      "id": 13,
      "type": "GPS",
      "title": "String",
      "dedication": "String",
      "photoPath": "cards/card_13/photo.jpg",
      "gpsLat": 45.12345,
      "gpsLon": 9.12345,
      "gpsRadiusMeters": 15.0,
      "gpsHint": "Indizio testuale per l'utente"
    },
    {
      "id": 14,
      "type": "IMAGE_MARKER", // Nota: nell'ultimo update era diventata GPS, usa il tipo scelto dall'utente o HYBRID
      "title": "String",
      "dedication": "String",
      "photoPath": "cards/card_14/photo.jpg",
      "markerPath": "cards/card_14/marker.jpg"
    },
    {
      "id": 15,
      "type": "HYBRID",
      "title": "String",
      "dedication": "String",
      "photoPath": "cards/card_15/photo.jpg",
      "markerPath": "cards/card_15/marker.jpg",
      "gpsLat": 45.12345,
      "gpsLon": 9.12345,
      "gpsRadiusMeters": 15.0,
      "gpsHint": "Indizio testuale"
    }
  ]
}
```

---

## 3. User Experience (Frontend Flow)
L'interfaccia deve essere a prova di errore, guidata e rassicurante. Struttura in un form Multi-Step.

*   **Step 0 (Obbligatorio & Bloccante):** Inserimento dell'Indirizzo Email. Testo UX: *"Dove dobbiamo spedire la tua applicazione completata?"*. Se l'email non è valida, l'utente non può iniziare a caricare le foto.
*   **Step 1: Quantità e Ricordi (TIMER).**
    *   Chiedi all'utente quante card vuole inserire in totale (es. min 3, max 30).
    *   Genera una UI a griglia dinamica in base al numero scelto.
    *   L'utente per ognuna delle slot carica 1 foto, inserisce un Titolo (max 30 char) e una Dedica (max 150 char). Di default le card sono di tipo TIMER.
*   **Step 2: Le Missioni Speciali (Opzionali - GPS/AR).**
    *   Permetti all'utente di trasformare le ultime card configurate (o qualsiasi card specifica) in missioni speciali.
    *   Mostrare una UI con mappa integrata (es. Google Maps Picker o Mapbox) per selezionare le coordinate GPS cliccando su un punto geografico.
    *   Richiedere l'inserimento del "gpsHint" (Indizio) obbligatorio per le card GPS.
    *   Per le card con marker AR, richiedere l'upload separato dell'immagine Marker, spiegando che deve essere una foto di un oggetto reale e ben contrastato.
*   **Step 3: Modulo di Pagamento (Placeholder).** Per ora il checkout è a costo zero. Includere un componente "Checkout" fittizio ma architetturalmente pronto ad accogliere l'SDK di Stripe in futuro.
*   **Step 4: Conferma e Attesa.** L'utente invia i dati. Mostrare un feedback chiaro: *"Stiamo creando la tua app. Riceverai un'email all'indirizzo [email] con il link per scaricare l'APK entro 5 minuti. Puoi chiudere questa pagina."*

---

## 4. Logica Backend e Integrazione GitHub (Cruciale)

La concorrenza deve essere gestita in modo infallibile per evitare sovrascritture di asset tra utenti diversi.

### Flusso di Generazione (Next.js Server Action / API Route):
1.  **Ricezione Dati:** Riceve il FormData dal frontend (numero variabile di foto, dati di testo, email).
2.  **Order ID:** Genera un UUID univoco per l'ordine (es. `order-123456`).
3.  **Creazione Branch:** Usa Octokit per creare una nuova branch su GitHub partendo dall'ultimo commit di `main`. Il nome della branch **DEVE** essere `build/order-123456`.
4.  **Commit Multiplo (Git Tree):** Invece di fare 20 commit separati, costruisci un Git Tree con Octokit contenente:
    *   Le immagini trasformate in Base64 (sia `photo.jpg` che `marker.jpg` nelle relative cartelle).
    *   Il `cards_config.json` generato ad-hoc.
    Fai un singolo commit sulla branch `build/order-123456` contenente tutti i file.
5.  **Trigger Action:** Usa Octokit per invocare l'endpoint `POST /repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches`. 
    *   Passare come `ref` il nome della branch appena creata (`build/order-123456`).
    *   Passare nei `client_payload` (inputs) l'indirizzo email dell'utente e l'OrderId.

### Gestione Ritorno (Webhook GitHub -> Next.js)
Il file `.github/workflows/android-build.yml` su GitHub verrà configurato in modo che, al termine della compilazione, esegua un comando `curl` (o usi un'Action webhook) verso un endpoint `/api/webhooks/github-build-done` del sito Next.js.
Questo payload conterrà l'URL per scaricare l'APK compilato e l'email dell'utente.
Il backend Next.js, ricevendo il webhook:
1.  Invierà un'email tramite Resend/SendGrid all'utente contenente un template curato e il pulsante per scaricare l'APK.

---

## 5. Cron Job di Pulizia (Retention Policy)
Le branch `build/*` contengono foto personali degli utenti e occupano spazio. Per privacy e pulizia, devono avere una vita massima di 7 giorni.
1.  Crea una route API protetta `/api/cron/cleanup-branches`.
2.  Configurala in `vercel.json` (o tramite provider) per essere chiamata una volta al giorno.
3.  La route deve usare Octokit per:
    *   Listare tutte le branch che iniziano con `build/`.
    *   Leggere la data del commit più recente di quella branch.
    *   Se la data è più vecchia di 7 giorni, eliminare la branch.

---

## 6. Sicurezza e Privacy (Must Have)
*   **Validazione lato Server:** Assicurati che i file caricati siano effettivamente immagini (MIME type check, dimensione massima es. 2MB a foto).
*   **Timeout:** I Serverless Functions hanno timeout (es. 10s su Vercel Hobby). L'upload e la creazione del tree su GitHub deve essere ottimizzata. Potrebbe essere necessario fare l'upload delle immagini dirette su un bucket S3 temporaneo, poi far scaricare a GitHub Actions i file dal bucket, *oppure* assicurarsi che le immagini vengano ridimensionate lato client prima dell'upload al server Next.js. Implementa la compressione lato client.

---
INIZIA A SCRIVERE IL CODICE PARTENDO DAL FRONTEND (UI COMPONENTS) E POI SPOSTATI SULL'ARCHITETTURA OCTOKIT BACKEND.