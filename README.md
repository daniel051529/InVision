# InVision 2.0

InVision 2.0 is a fresh Android app project for QR anchored ARCore indoor maps.

Core flow:

1. Open the app.
2. Tap `Create Map`.
3. Scan the fixed wall QR code for that physical map location.
4. Name the map.
5. Enter AR mode for that map.
6. Pick a virtual marker type, such as `T-Rex`.
7. Tap detected AR surfaces to place saved navigation landmarks.
8. Use `Delete Last Marker` in AR mode to remove a placed marker.
9. Tap `Saved Maps` to view saved maps, rename maps, open AR, inspect the map file, see the QR payload, or delete a map.

Accepted QR formats:

```text
invision://map/main_lobby
```

```json
{"mapId":"main_lobby","name":"Main Lobby"}
```

Marker data is saved locally per QR map with each marker's type and AR pose. Route generation can be layered onto that saved map data later.

3D marker models:

- `T-Rex` -> `app/src/main/assets/models/T-Rex.glb`
- `Triceratops` -> `app/src/main/assets/models/Triceratops.glb`
- `Stegosaurus` -> `app/src/main/assets/models/Stegosaurus.glb`
- `Apatosaurus` -> `app/src/main/assets/models/Apatosaurus.glb`
- `Velociraptor` -> `app/src/main/assets/models/Velociraptor.glb`
- `Parasaurolophus` -> `app/src/main/assets/models/Parasaurolophus.glb`

Current AR behavior:

- Scanning the same QR map ID loads the same saved marker file again.
- The saved file stores each marker's type and pose.
- Exact real-world reappearance across app launches still needs a persistent anchor or QR pose calibration step. The current version reloads saved ARCore coordinates for the map, but ARCore's world origin may shift between sessions.
