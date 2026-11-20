# Sentieri — Android nativo

Progetto Android nativo scritto in Kotlin, ricreato a partire dalle funzioni dell'app `escursioni`.

## Avvio

Aprire questa cartella in Android Studio e attendere la sincronizzazione Gradle. In alternativa, con Android SDK e JDK 17 configurati:

```bash
./gradlew assembleDebug
```

## Funzioni incluse

- Riepilogo posizione, meteo, punti d'interesse e statistiche dell'uscita.
- Mappa OpenStreetMap con ricerca per nome o coordinate, filtro per tipo e selezione del punto da segnalare.
- Stili cartografici standard, satellitare e terreno, più vista 3D con rilievo.
- Tracciamento GPS, percorso visualizzato sulla mappa, distanza, dislivello positivo/negativo, pendenza e bussola.
- Punti d'interesse iniziali di Valle Aurina, Dolomiti e Trentino, segnalazioni personali persistenti e ricerca di punti vicini su OpenStreetMap.
- Diario locale delle escursioni.
- Consigli di percorso in base al livello e alle preferenze.

I dati personali (diario, percorso e segnalazioni) sono salvati sul dispositivo. Meteo, mappe, tessere cartografiche e sincronizzazione OSM richiedono una connessione Internet. La schermata dei consigli offre suggerimenti locali e non interroga Gemini: nell'app originale l'AI funzionava solo con `GEMINI_API_KEY` configurata. Per riattivarla in modo adatto a un'app distribuita serve un piccolo backend che custodisca la chiave, senza inserirla nell'app Android.

## Idee utili per una prossima versione

1. Mappe e tracce scaricabili per l'uso senza rete, con gestione esplicita dello spazio occupato.
2. Importazione ed esportazione GPX e condivisione di un'escursione.
3. Avvisi di deviazione dalla traccia e punti di controllo SOS, anche con posizione e contatto d'emergenza configurati dall'utente.
4. Schede di sicurezza con bollettini meteo in quota e stato aggiornato dei sentieri, se disponibili da fonti affidabili.
5. Backup e sincronizzazione facoltativa tra dispositivi.
