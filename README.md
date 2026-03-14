# 🐕 Dog Safe — CROW Act Dog Restriction Checker

> Know before you go. Check live dog restrictions on CROW access land across England.

[![Build Debug APK](https://github.com/boothy91/dog-safe/actions/workflows/build-debug.yml/badge.svg)](https://github.com/boothy91/dog-safe/actions/workflows/build-debug.yml)

---

## What Is This?

Dog Safe is an Android app and web tool that shows live dog walking restrictions on Countryside and Rights of Way (CROW) Act 2000 access land in England.

Many dog owners don't realise that open access land — moorland, heath, down, and registered common land — can have legal restrictions on dogs. These restrictions are issued by Natural England and can last up to 5 years. They're invisible on the ground, with no physical signs in many cases.

Dog Safe makes them visible.

---

## Features

- 🗺️ **Live map** — OSM base map with restriction polygons overlaid
- 🔴🟠🟡 **Colour coded** — instantly see No Dogs, No Dogs except assistance, Dogs on Lead zones
- 📍 **GPS location** — jumps to your current position
- 🔍 **Search** — find any postcode, town, village or area name in England
- 📄 **Official PDFs** — tap any restriction to view the legal Natural England map
- ✅ **Active filter** — show only current restrictions, or all including expired
- 🐕 **Quick areas** — one tap to Yorkshire Dales, Peak District, Dartmoor, Lake District, North York Moors, Exmoor

---

## Dog Rules on CROW Access Land

| Situation | Rule |
|---|---|
| General access | Dogs allowed |
| Near livestock | Short lead (max 2m) — year round |
| 1 March – 31 July | Short lead near ground-nesting birds |
| Active No Dogs restriction | Dogs not permitted |
| Active No Dogs (except assistance) | Only registered assistance dogs |
| Active Dogs on Lead restriction | Lead required throughout |

---

## Data Sources

All data is live from official government APIs — no local database, no server needed.

| Data | Source | Licence |
|---|---|---|
| CROW access land polygons | Natural England · `CRoW_Act_2000_Access_Layer` | Open Government Licence |
| Dog restrictions | Natural England · `OASYS_RESTRICTION_PRD_VIEW` | Open Government Licence |
| Restriction PDFs | `openaccess.naturalengland.org.uk/[CASE_NUMBER].pdf` | Open Government Licence |
| Map tiles | OpenStreetMap via OSMDroid | ODbL |
| Geocoding / search | Nominatim (OpenStreetMap) | ODbL |

**Attribution:** © Natural England copyright. Contains Ordnance Survey data © Crown copyright and database right 2026.

---

## API Endpoints

**CROW Access Land**
```
https://services.arcgis.com/JJzESW51TqeY9uat/arcgis/rest/services/CRoW_Act_2000_Access_Layer/FeatureServer/0/query
```

**Dog Restrictions (live)**
```
https://services.arcgis.com/JJzESW51TqeY9uat/arcgis/rest/services/OASYS_RESTRICTION_PRD_VIEW/FeatureServer/0/query
```

**Example — active dog restrictions near a location:**
```
?geometry=-2.1,54.0,-1.9,54.2
&geometryType=esriGeometryEnvelope
&inSR=4326
&where=(TYPE='02' OR TYPE='04') AND END_DATE>=CURRENT_TIMESTAMP AND START_DATE<=CURRENT_TIMESTAMP
&outFields=CASE_NUMBER,TYPE,PURPOSE,START_DATE,END_DATE,VIEW_MAP
&outSR=4326&f=json
```

**Restriction type codes:**

| Code | Meaning |
|---|---|
| 02 | No Dogs |
| 03 | No Dogs (except guide or hearing dogs) |
| 04 | No Dogs (except guide, hearing or assistance dogs) |
| 05 | Dogs on Leads |
| 09 | Dogs on Fenced Routes Only |
| 10 | Marked Routes, Dogs on Leads |

---

## Tech Stack

**Android App**
- Kotlin + Android SDK 35
- OSMDroid — offline-capable map rendering
- Retrofit + OkHttp — API calls
- Nominatim — free geocoding, no API key required
- Material Components 1.12
- GitHub Actions — automated APK builds

**Web Preview**
- Single file HTML + Leaflet.js
- Hosted on GitHub Pages
- Pulls live data from Natural England API

---

## Project Structure

```
DogSafe/
├── index.html                          ← Web preview (GitHub Pages)
├── android/
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/dogsafe/app/
│   │   │   ├── MainActivity.kt         ← Map, search, location, bottom sheet
│   │   │   ├── RestrictionPolygonOverlay.kt  ← OSMDroid polygon drawing + tap
│   │   │   ├── api/ApiClient.kt        ← Natural England API + response parsing
│   │   │   ├── model/Restriction.kt    ← Data model + restriction type enum
│   │   │   ├── search/GeocodingClient.kt  ← Nominatim geocoding
│   │   │   └── viewmodel/MapViewModel.kt  ← Debounced map queries + LiveData
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/themes.xml
└── .github/workflows/
    └── build-debug.yml                 ← GitHub Actions debug build
```

---

## Building

### Debug APK (GitHub Actions)
Push to `main` — Actions builds automatically. Download from the Actions tab.

### Local build
```bash
cd android
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Roadmap

- [ ] Release signing + Play Store submission
- [ ] Offline map support for walked areas
- [ ] All restriction types (not just dogs)
- [ ] Seasonal lead reminder banner (1 Mar – 31 Jul)
- [ ] Share a restriction with other walkers
- [ ] Wales & Scotland access land data

---

## Disclaimer

This app displays official Natural England data for informational purposes. Always check physical signs on the ground. Restrictions can be added or changed at any time. For the most authoritative information visit [gov.uk/right-of-way-open-access-land](https://www.gov.uk/right-of-way-open-access-land/use-your-right-to-roam).

---

## Author

[@boothy91](https://github.com/boothy91) · Also builds [WristNav](https://github.com/boothy91/wristnav) — standalone Wear OS GPX navigation
