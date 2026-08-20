# Player Icon UI

Player icons provide compact visual identity for each registered player throughout the Android app.

## Source assets (read-only)

```text
Resources/Icons/
├── Aeroplane.png
├── Car.png
├── Helicopter.png
└── Ship.png
```

Source files are never modified in place.

## Generated Android resources

```text
python tools/sync_android_icons.py
```

Copies icons into:

```text
android-app/app/src/main/res/drawable/
├── player_aeroplane.png
├── player_car.png
├── player_helicopter.png
└── player_ship.png
```

Manifest: `data/android_player_icon_manifest.json`

## Stable Player ID mapping

```text
USR_01 → player_car
USR_02 → player_helicopter
USR_03 → player_ship
USR_04 → player_aeroplane
```

Lookup is always by `playerId` / `cardId` through `PlayerIconRegistry` — never by display-name string matching.

## UI component

```text
playerId
   ↓
PlayerIconRegistry
   ↓
PlayerIdentity(...) Compose component
```

Sizing conventions:

| Size   | dp  | Usage                          |
|--------|-----|--------------------------------|
| Small  | 24  | History rows, compact lists    |
| Normal | 36  | Dashboards, labels, setup      |
| Large  | 80  | User scan, winner, player info |

Icons use `ContentScale.Fit`, no theme tint, and always render **icon + player name**.

## Not persisted

Icons are static app resources. Nothing icon-related is stored in `GameSession`.

## Validation

```text
python tools/validate_player_icons.py
```

Writes `data/player_icon_validation.txt`.

## Resources layout

```text
Resources/
├── Cards/
├── Icons/
│   ├── Aeroplane.png
│   ├── Car.png
│   ├── Helicopter.png
│   └── Ship.png
└── Sounds/
```
