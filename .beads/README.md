# Beads-Kontext für morphe-patches

Diese lokale Beads-Datenbank speichert Aufgaben, Abhängigkeiten und ausdrücklich hinterlegtes Projektwissen. Harness Router lädt ihren Kontext vor jedem Auftrag automatisch; eine Aufgabenauswahl ist optional.

- Kontext prüfen: `bd prime --full --readonly`
- Verfügbare Aufgaben lesen: `bd ready --json --readonly`
- Wissen ausdrücklich hinterlegen: `bd remember "Projektwissen"`

Projektregeln bleiben in `AGENTS.md`. Initialisierung ohne Änderungen an Regeln und Git-Hooks. Aufgaben werden vom Router weder automatisch übernommen noch geschlossen.

Die Datenbank unter `embeddeddolt/` bleibt lokal und wird nicht durch normale Git-Commits gesichert. Versionierte Konfigurationsdateien ersetzen kein Datenbank-Backup; Datenübertragung und Sicherung erfolgen separat über Beads/Dolt.
