# 🐕 Dog Safe

> Live CROW Act dog restriction checker for England

Browse CROW access land dog restrictions by area, powered entirely by Natural England's live API. No server, no database — just real government data.

## Live Demo

[boothy91.github.io/dog-safe](https://boothy91.github.io/dog-safe)

## Features

- Live dog restrictions from Natural England
- Filtered by active date
- Direct links to official restriction PDFs
- Covers all CROW access land in England

## API Sources

| Data | Endpoint |
|---|---|
| Access Land | `CRoW_Act_2000_Access_Layer` FeatureServer |
| Dog Restrictions | `OASYS_RESTRICTION_PRD_VIEW` FeatureServer |
| PDFs | `https://openaccess.naturalengland.org.uk/[CASE_NUMBER].pdf` |

Base URL: `https://services.arcgis.com/JJzESW51TqeY9uat/arcgis/rest/services/`

## Restriction Types

| Code | Description |
|---|---|
| 02 | No Dogs |
| 03 | No Dogs (except guide/hearing dogs) |
| 04 | No Dogs (except assistance dogs) |
| 05 | Dogs on Leads |
| 09 | Dogs on Fenced Routes Only |
| 10 | Marked Routes, Dogs on Leads |

## Roadmap

- [ ] Android app (Kotlin + Jetpack Compose)
- [ ] GPS-based location detection
- [ ] Map overlay with restriction polygons
- [ ] Offline support for walked areas
- [ ] Push notifications for new restrictions

## Data Licence

Natural England Open Government Licence. Contains OS data © Crown Copyright.

## Author

[@boothy91](https://github.com/boothy91)
