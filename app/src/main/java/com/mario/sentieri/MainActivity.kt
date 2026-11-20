package com.mario.sentieri

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

data class Poi(val name: String, val type: String, val lat: Double, val lng: Double, val altitude: Int, val description: String)
data class TrackPoint(val lat: Double, val lng: Double, val altitude: Double)
data class Hike(val name: String, val date: String, val distance: Double, val minutes: Int, val altitude: Int, val notes: String)

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var db: TrackDatabase
    private lateinit var root: LinearLayout
    private var page = "dashboard"
    private var lat = 46.9953
    private var lng = 11.9856
    private var altitude = 0.0
    private var weather = "Meteo: caricamento…"
    private var tracking = false
    private var distance = 0.0
    private var gain = 0.0
    private var loss = 0.0
    private var slope = 0.0
    private var compass = 0
    private var mapMenuOpen = true
    private var currentMapStyle = "standard"
    private var points = mutableListOf<TrackPoint>()
    private val altitudeHistory = mutableListOf<Double>()
    private var diary = mutableListOf<Hike>()
    private val pois = mutableListOf(
        Poi("Picco dei Tre Signori", "vetta", 47.0708, 12.1406, 3499, "Vetta al confine tra Italia e Austria."),
        Poi("Rifugio Sasso Nero", "rifugio", 47.0181, 11.8706, 3026, "Rifugio con vista sui ghiacciai."),
        Poi("Malga Kehrer", "malga", 47.0396, 12.1122, 1842, "Tipica malga della Valle Aurina."),
        Poi("Cascate di Riva", "punto interesse", 46.9248, 11.9542, 1000, "Cascate naturali."),
        Poi("Rifugio Tridentina", "rifugio", 47.0678, 12.1039, 2441, "Rifugio storico."),
        Poi("Tofana di Rozes", "vetta", 46.5401, 12.0498, 3225, "Vetta delle Dolomiti Ampezzane."),
        Poi("Rifugio Lagazuoi", "rifugio", 46.5478, 12.0085, 2752, "Terrazza panoramica sulle Alpi."),
        Poi("Tre Cime di Lavaredo", "vetta", 46.6186, 12.3023, 2999, "Simbolo delle Dolomiti."),
        Poi("Cinque Torri", "punto interesse", 46.5186, 12.0494, 2361, "Museo a cielo aperto della Grande Guerra."),
        Poi("Rifugio Vajolet", "rifugio", 46.4633, 11.6377, 2243, "Nel cuore del Catinaccio."),
        Poi("Torri del Vajolet", "vetta", 46.4678, 11.6444, 2821, "Guglie spettacolari."),
        Poi("Lago di Carezza", "lago", 46.4093, 11.5750, 1534, "Il lago dell’arcobaleno sotto il Latemar.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TrackDatabase(this)
        window.statusBarColor = Color.rgb(23, 61, 50)
        window.navigationBarColor = Color.rgb(23, 61, 50)
        loadSaved()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 247, 243)) }
        setContentView(root)
        render()
        fetchWeather()
        requestSingleLocation()

        TrackingService.onLocationUpdated = { loc ->
            runOnUiThread { handleNewLocation(loc) }
        }
    }

    private fun loadSaved() {
        points = db.loadPoints()
        diary = db.loadDiary()
        val savedPois = db.loadPois()
        if (savedPois.isNotEmpty()) {
            pois.clear()
            pois.addAll(savedPois)
        }
        if (points.isNotEmpty()) {
            val last = points.last()
            lat = last.lat
            lng = last.lng
            altitude = last.altitude
        }
        distance = 0.0
        for (i in 1 until points.size) {
            val res = FloatArray(1)
            Location.distanceBetween(points[i-1].lat, points[i-1].lng, points[i].lat, points[i].lng, res)
            distance += res[0] / 1000.0
        }
    }

    private fun save() {
        db.savePoints(points)
        db.saveDiary(diary)
        db.savePois(pois)
    }

    private fun render() {
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 15, 20, 12); setBackgroundColor(Color.rgb(23, 61, 50)) }
        header.addView(label("SENTIERI", 22, Color.WHITE, true)); header.addView(label("Esplora • registra • ricorda", 13, Color.rgb(209, 225, 215), false)); root.addView(header)

        if (page == "map" && !mapMenuOpen) {
            val frame = FrameLayout(this)
            val map = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = "SentieriAndroidApp/1.0 (Hiking App)"
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(MapBridge(), "AndroidMap")
            }
            frame.addView(map, FrameLayout.LayoutParams(-1, -1))

            val html = mapHtml()
            map.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)

            val menuBtn = Button(this).apply {
                text = "☰ Menu"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(47, 118, 91))
                textSize = 12f
                setPadding(20, 10, 20, 10)
                setOnClickListener {
                    mapMenuOpen = true
                    render()
                }
            }
            val btnParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(0, 30, 30, 0)
            }
            frame.addView(menuBtn, btnParams)

            root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val body = ScrollView(this)
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 20) }
            body.addView(content)
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

            when (page) {
                "dashboard" -> dashboard(content)
                "map" -> mapScreen(content)
                "diary" -> diaryScreen(content)
                else -> advisorScreen(content)
            }
        }

        val nav = LinearLayout(this).apply { setPadding(5, 7, 5, 7); setBackgroundColor(Color.WHITE) }
        listOf("dashboard" to "⌂\nOggi", "map" to "⌖\nMappa", "diary" to "▤\nDiario", "ai" to "✦\nConsigli").forEach { (id, title) ->
            val b = Button(this).apply { text = title; textSize = 11f; setTextColor(if (page == id) Color.rgb(36, 112, 80) else Color.DKGRAY); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { page = id; render() } }
            nav.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val navParams = LinearLayout.LayoutParams(-1, -2)
        navParams.setMargins(0, 0, 0, 100)
        root.addView(nav, navParams)
    }

    private fun dashboard(c: LinearLayout) {
        c.addView(label("Riepilogo esploratore", 23, Color.rgb(31, 57, 45), true)); c.addView(label("$weather  •  quota ${altitude.roundToInt()} m", 15, Color.DKGRAY, false))
        card(c, "La tua attività") { addView(label("Distanza  ${fmt(distance)} km      ↑ ${gain.roundToInt()} m      ↓ ${loss.roundToInt()} m", 16, Color.rgb(35, 82, 59), true)) }
        if (altitudeHistory.size > 1) card(c, "Profilo altimetrico recente") { addView(label(altitudeHistory.map { "▂▃▅▇"[((it - (altitudeHistory.minOrNull() ?: it)) / ((altitudeHistory.maxOrNull() ?: it) - (altitudeHistory.minOrNull() ?: it) + 1) * 3).roundToInt().coerceIn(0, 3)] }.joinToString(" "), 20, Color.rgb(47, 118, 91), true)) }
        card(c, "Posizione") { addView(label(String.format(Locale.ITALY, "%.5f, %.5f", lat, lng), 15, Color.DKGRAY, false)); addView(label("Bussola: ${compass}°   •   Pendenza: ${fmt(slope)}%", 13, Color.GRAY, false)) }
        card(c, "Punti d’interesse vicini") { pois.take(5).forEach { addView(label("${it.name} · ${it.altitude} m", 14, Color.DKGRAY, false)) } }
        card(c, "Diario") { addView(label("${diary.size} escursioni registrate", 15, Color.DKGRAY, false)); action(this, "Apri diario") { page = "diary"; render() } }
        action(c, if (tracking) "Ferma tracciamento" else "Inizia tracciamento GPS") { toggleTracking() }
    }

    private fun mapScreen(c: LinearLayout) {
        if (mapMenuOpen) {
            c.addView(label("Mappa e punti d’interesse", 22, Color.rgb(31, 57, 45), true))
            
            action(c, "▲ Chiudi menu opzioni") {
                mapMenuOpen = false
                render()
            }

            val search = EditText(this).apply { hint = "Cerca luogo o coordinate (46.5, 11.8)"; maxLines = 1 }
            val filters = EditText(this).apply { hint = "Filtra per tipo (vetta, rifugio, lago…)"; maxLines = 1 }
            c.addView(search)
            c.addView(filters)

            action(c, "Mappa standard") {
                currentMapStyle = "standard"
                mapMenuOpen = false
                render()
            }
            action(c, "Mappa satellitare") {
                currentMapStyle = "satellite"
                mapMenuOpen = false
                render()
            }
            action(c, "Mappa terreno") {
                currentMapStyle = "terrain"
                mapMenuOpen = false
                render()
            }
            action(c, if (tracking) "Ferma tracciamento" else "Inizia tracciamento") { toggleTracking() }
            if (points.isNotEmpty()) {
                action(c, "Esporta GPX traccia corrente") { exportCurrentGpx() }
                action(c, "Azzera percorso e statistiche") { points.clear(); altitudeHistory.clear(); distance = 0.0; gain = 0.0; loss = 0.0; slope = 0.0; save(); render() }
            }
            action(c, "Importa traccia GPX") { promptImportGpx() }
            action(c, "Sincronizza punti OpenStreetMap") { fetchOsm() }
            action(c, "Segnala un punto sulla mappa") { reportDialog(lat, lng) }

            val mapWrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    cornerRadii = floatArrayOf(30f, 30f, 30f, 30f, 0f, 0f, 0f, 0f)
                }
                clipToOutline = true
            }

            val map = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = "SentieriAndroidApp/1.0 (Hiking App)"
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(MapBridge(), "AndroidMap")
            }
            mapWrapper.addView(map, LinearLayout.LayoutParams(-1, 320))
            c.addView(mapWrapper, LinearLayout.LayoutParams(-1, -2))

            val html = mapHtml()
            map.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)

            search.setOnEditorActionListener { _, _, _ ->
                val raw = search.text.toString().trim()
                val coords = Regex("^([-+]?\\d+\\.?\\d*)[,\\s]+([-+]?\\d+\\.?\\d*)$").find(raw)
                if (coords != null) {
                    val a = coords.groupValues[1].toDoubleOrNull()
                    val b = coords.groupValues[2].toDoubleOrNull()
                    if (a != null && b != null) map.evaluateJavascript("setCenter($b,$a)", null)
                } else {
                    val target = pois.firstOrNull { it.name.contains(raw, true) }
                    if (target != null) map.evaluateJavascript("setCenter(${target.lng},${target.lat})", null)
                }
                true
            }
            filters.setOnEditorActionListener { _, _, _ ->
                map.evaluateJavascript("filterMarkers('${filters.text.toString().replace("'", "")}')", null)
                true
            }
        } else {
            val frame = FrameLayout(this)
            
            val map = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = "SentieriAndroidApp/1.0 (Hiking App)"
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(MapBridge(), "AndroidMap")
            }
            frame.addView(map, FrameLayout.LayoutParams(-1, -1))

            val html = mapHtml()
            map.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)

            val menuBtn = Button(this).apply {
                text = "☰ Menu"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(47, 118, 91))
                textSize = 12f
                setPadding(20, 10, 20, 10)
                setOnClickListener {
                    mapMenuOpen = true
                    render()
                }
            }
            val btnParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(0, 30, 30, 0)
            }
            frame.addView(menuBtn, btnParams)

            c.addView(frame, LinearLayout.LayoutParams(-1, 680))
        }
    }

    private fun mapHtml(): String {
        val markers = JSONArray(); pois.forEach { markers.put(JSONObject().put("name", it.name).put("type", it.type).put("lat", it.lat).put("lng", it.lng).put("alt", it.altitude).put("description", it.description)) }
        val path = JSONArray(); points.forEach { path.put(JSONArray().put(it.lat).put(it.lng)) }
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><style>html,body,#map{height:100%;margin:0}#map{background:#dce6df}</style></head><body><div id="map"></div><script>
            const map=L.map('map').setView([$lat,$lng],13); let baseLayer; const trailLayer=L.tileLayer('https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png',{opacity:0.7,attribution:'© Waymarked Trails'}).addTo(map);
            function setMapStyle(style){if(baseLayer)map.removeLayer(baseLayer);const urls={standard:'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',satellite:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',terrain:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}'};baseLayer=L.tileLayer(urls[style]||urls.standard,{maxZoom:19,attribution:style==='standard'?'© OpenStreetMap':'Map data'}).addTo(map);baseLayer.bringToBack();}
            setMapStyle('$currentMapStyle');
            const data=__MARKERS__;const markers=[];data.forEach(p=>{const m=L.marker([p.lat,p.lng]).addTo(map).bindPopup('<b>'+p.name+'</b><br>'+p.type+' · '+p.alt+' m<br>'+p.description);m.poiType=p.type;m.poiName=p.name;markers.push(m)});
            const path=__PATH__;if(path.length>0)L.polyline(path,{color:'#278451',weight:5}).addTo(map);
            L.marker([$lat,$lng]).addTo(map).bindPopup('Posizione attuale'); map.on('click',e=>AndroidMap.onMapClick(e.latlng.lat,e.latlng.lng));
            function setCenter(lng,lat){map.setView([lat,lng],13)} function filterMarkers(q){q=q.toLowerCase();markers.forEach(m=>{if(!q||m.poiType.includes(q)||m.poiName.toLowerCase().includes(q))m.addTo(map);else map.removeLayer(m)})}
            </script></body></html>""".replace("__MARKERS__", markers.toString()).replace("__PATH__", path.toString())
    }

    inner class MapBridge { @JavascriptInterface fun onMapClick(a: Double, b: Double) { runOnUiThread { reportDialog(a, b) } } }

    private fun diaryScreen(c: LinearLayout) {
        c.addView(label("Diario & Vette", 23, Color.rgb(31, 57, 45), true)); action(c, "＋ Registra escursione") { addHikeDialog() }
        if (diary.isEmpty()) c.addView(label("Le tue escursioni salvate appariranno qui.", 15, Color.GRAY, false))
        diary.forEachIndexed { index, h ->
            card(c, h.name) {
                addView(label("${h.date} · ${fmt(h.distance)} km · ${h.minutes} min · quota ${h.altitude} m", 14, Color.DKGRAY, false))
                if (h.notes.isNotBlank()) addView(label(h.notes, 14, Color.GRAY, false))
                action(this, "Elimina escursione") {
                    diary.removeAt(index)
                    save()
                    render()
                }
            }
        }
    }

    private fun advisorScreen(c: LinearLayout) {
        c.addView(label("Consigli per la montagna", 22, Color.rgb(31, 57, 45), true)); c.addView(label("Indica esperienza e preferenze per ricevere idee di escursione.", 14, Color.DKGRAY, false))
        val skill = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Turistico (T)", "Facile", "Intermedio", "Avanzato", "Esperto", "Via Ferrata")) }; c.addView(skill)
        val prefsText = EditText(this).apply { hint = "Laghi, boschi, ferrate…"; setText("Laghi e sentieri boschivi") }; c.addView(prefsText)
        val result = TextView(this).apply { textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, 14, 0, 14) }; c.addView(result)
        action(c, "Chiedi un consiglio") { result.text = makeAdvice(skill.selectedItem.toString(), prefsText.text.toString()) }
        c.addView(label("Meteo attuale: $weather", 14, Color.GRAY, false))
    }

    private fun makeAdvice(skill: String, interests: String): String {
        val isHard = skill.contains("Avanzato") || skill.contains("Esperto") || skill.contains("Ferrata")
        val pick = if (isHard) "Rifugio Lagazuoi (2.752 m), su itinerari d'alta quota: scegli il percorso in base alla tua preparazione e verifica l'eventuale presenza di tratti attrezzati." else when { interests.contains("lag", true) -> "Lago di Carezza (1.534 m), con itinerari nel bosco ai piedi del Latemar."; interests.contains("rifug", true) -> "Rifugio Lagazuoi (2.752 m), raggiungibile con itinerari panoramici nell’area del Falzarego."; else -> "Cascate di Riva e i sentieri della Valle Aurina, adatti a un’uscita modulabile." }
        return "Proposta per livello $skill:\n\n$pick\n\nVerifica sempre meteo e condizioni del percorso prima di partire. In quota il tempo cambia rapidamente; porta acqua, abbigliamento adeguato e una traccia offline. Preferenze: $interests."
    }

    private fun card(parent: LinearLayout, title: String, block: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(15, 12, 15, 12); setBackgroundColor(Color.WHITE) }
        val params = LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 11, 0, 2); parent.addView(box, params)
        box.addView(label(title, 16, Color.rgb(37, 88, 63), true)); box.block()
    }
    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 5, 0, 5) }
    private fun action(parent: LinearLayout, title: String, click: () -> Unit) { parent.addView(Button(this).apply { text = title; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(47, 118, 91)); setOnClickListener { click() } }) }
    private fun fmt(v: Double) = String.format(Locale.ITALY, "%.2f", v)

    private fun toggleTracking() {
        tracking = !tracking
        if (tracking) {
            if (!hasLocationPermission()) {
                requestLocationPermission()
                tracking = false
                return
            }
            val serviceIntent = Intent(this, TrackingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Tracciamento avviato (Servizio in background)", Toast.LENGTH_SHORT).show()
        } else {
            stopService(Intent(this, TrackingService::class.java))
            Toast.makeText(this, "Tracciamento fermato", Toast.LENGTH_SHORT).show()
        }
        save()
        render()
    }

    private fun handleNewLocation(location: Location) {
        lat = location.latitude
        lng = location.longitude
        if (location.hasAltitude()) {
            val newAlt = location.altitude
            val prevAlt = if (altitudeHistory.isNotEmpty()) altitudeHistory.last() else newAlt
            altitude = newAlt
            altitudeHistory.add(altitude)
            while (altitudeHistory.size > 10) altitudeHistory.removeAt(0)

            val delta = newAlt - prevAlt
            if (kotlin.math.abs(delta) > 1.0) {
                if (delta > 0) gain += delta else loss -= delta
            }
        }
        val currentPoint = TrackPoint(lat, lng, altitude)
        val previous = points.lastOrNull()
        if (previous != null) {
            val result = FloatArray(1)
            Location.distanceBetween(previous.lat, previous.lng, currentPoint.lat, currentPoint.lng, result)
            if (result[0] > 3 && result[0] < 300) {
                distance += result[0] / 1000.0
                slope = (altitude - previous.altitude) / result[0] * 100
            }
        }
        if (previous == null || kotlin.math.abs(currentPoint.lat - previous.lat) + kotlin.math.abs(currentPoint.lng - previous.lng) > 0.00001) {
            points.add(currentPoint)
        }
        save()
        if (page == "dashboard") render()
    }

    private fun hasLocationPermission() = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 5)
    }

    private fun requestSingleLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val provider = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.getLastKnownLocation(provider)?.let {
                lat = it.latitude
                lng = it.longitude
                if (it.hasAltitude()) altitude = it.altitude
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (!tracking) {
            requestSingleLocation()
        }
        val sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            SensorManager.getOrientation(matrix, orientation)
            compass = ((Math.toDegrees(orientation[0].toDouble()).toInt() + 360) % 360)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun fetchWeather() {
        thread {
            try {
                val text = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code").readText()
                val current = JSONObject(text).getJSONObject("current")
                val condition = when (current.optInt("weather_code")) {
                    0 -> "sereno"
                    1, 2 -> "parzialmente nuvoloso"
                    3 -> "coperto"
                    45, 48 -> "nebbia"
                    in 51..67 -> "pioggia"
                    in 71..77 -> "neve"
                    in 95..99 -> "temporale"
                    else -> "variabile"
                }
                weather = "${current.optDouble("temperature_2m").roundToInt()}° · $condition · vento ${current.optDouble("wind_speed_10m").roundToInt()} km/h"
                runOnUiThread { if (page == "dashboard" || page == "ai") render() }
            } catch (_: Exception) {
                weather = "Meteo non disponibile"
            }
        }
    }

    private fun fetchOsm() {
        Toast.makeText(this, "Ricerca punti OpenStreetMap…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val query = "[out:json];(node(around:12000,$lat,$lng)[\"tourism\"~\"alpine_hut|viewpoint\"];node(around:12000,$lat,$lng)[\"natural\"~\"peak|water\"];);out body 30;"
                val body = "data=" + URLEncoder.encode(query, "UTF-8")
                val conn = URL("https://overpass-api.de/api/interpreter?$body").openConnection()
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                val json = JSONObject(conn.getInputStream().bufferedReader().readText()).optJSONArray("elements") ?: JSONArray()
                var added = 0
                for (i in 0 until json.length()) {
                    val el = json.optJSONObject(i) ?: continue
                    val tags = el.optJSONObject("tags") ?: continue
                    val name = tags.optString("name")
                    if (name.isBlank() || pois.any { it.name == name }) continue
                    val kind = tags.optString("tourism", tags.optString("natural", "punto interesse"))
                    pois.add(Poi(name, kind, el.optDouble("lat"), el.optDouble("lon"), tags.optString("ele").toDoubleOrNull()?.roundToInt() ?: 0, "Punto OpenStreetMap"))
                    added++
                }
                save()
                runOnUiThread {
                    Toast.makeText(this, "$added nuovi punti trovati", Toast.LENGTH_LONG).show()
                    render()
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "Sincronizzazione OSM non riuscita", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun exportCurrentGpx() {
        try {
            val file = java.io.File(getExternalFilesDir(null), "traccia_corrente.gpx")
            file.outputStream().use { out ->
                Gpx.exportGpx(points, "Escursione Attuale", out)
            }
            Toast.makeText(this, "GPX esportato in: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore esportazione GPX: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptImportGpx() {
        val input = EditText(this).apply { hint = "Incolla contenuto GPX o XML qui" }
        AlertDialog.Builder(this)
            .setTitle("Importa GPX")
            .setView(input)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Importa") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    try {
                        val imported = Gpx.importGpx(text.byteInputStream())
                        if (imported.isNotEmpty()) {
                            points.clear()
                            points.addAll(imported)
                            save()
                            render()
                            Toast.makeText(this, "Traccia GPX importata (${imported.size} punti)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Nessun punto valido trovato nel GPX", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Errore importazione: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun reportDialog(a: Double, b: Double) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 5, 20, 0) }
        val name = EditText(this).apply { hint = "Nome (es. frana sentiero 12)"; maxLines = 1 }
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("pericolo", "vetta", "rifugio", "malga", "lago", "fontana", "panchina", "punto interesse")) }
        val desc = EditText(this).apply { hint = "Descrizione o note utili" }
        form.addView(name); form.addView(type); form.addView(desc)
        AlertDialog.Builder(this).setTitle("Nuova segnalazione · %.5f, %.5f".format(a, b)).setView(form).setNegativeButton("Annulla", null).setPositiveButton("Salva") { _, _ -> if (name.text.isNotBlank()) { pois.add(Poi(name.text.toString(), type.selectedItem.toString(), a, b, altitude.roundToInt(), desc.text.toString())); save(); render() } }.show()
    }

    private fun addHikeDialog() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 5, 20, 0) }
        val name = EditText(this).apply { hint = "Nome percorso"; maxLines = 1 }
        val km = EditText(this).apply { hint = "Distanza (km)"; maxLines = 1; inputType = 8194 }
        val minutes = EditText(this).apply { hint = "Durata (minuti)"; maxLines = 1; inputType = 2 }
        val maxAlt = EditText(this).apply { hint = "Quota massima (m)"; maxLines = 1; inputType = 2 }
        val notes = EditText(this).apply { hint = "Note" }
        listOf(name, km, minutes, maxAlt, notes).forEach { form.addView(it) }
        AlertDialog.Builder(this).setTitle("Registra escursione").setView(form).setNegativeButton("Annulla", null).setPositiveButton("Salva") { _, _ -> if (name.text.isNotBlank()) { diary.add(0, Hike(name.text.toString(), java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(java.util.Date()), km.text.toString().toDoubleOrNull() ?: distance, minutes.text.toString().toIntOrNull() ?: 0, maxAlt.text.toString().toIntOrNull() ?: altitude.roundToInt(), notes.text.toString())); save(); render() } }.show()
    }
}
