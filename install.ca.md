# Instal·lar MycoTrack

MycoTrack es distribueix fora de Google Play: s'instal·la un **APK** signat directament des
de la [pàgina de publicacions de GitHub](https://github.com/elricdog/MycoTrack/releases).
Aquesta pàgina explica, pas a pas, com fer-ho a qualsevol telèfon o tauleta compatible i
com mantenir l'aplicació actualitzada.

- **Requisits:** Android 8.0 (API 26) o superior. **No calen els serveis de Google Play**,
  ni compte, ni clau d'API, ni subscripció.
- **Emmagatzematge:** l'aplicació ocupa uns 100 MB; les àrees de mapes offline són extra
  (les descarregues tu al dispositiu quan vulguis).
- **Idiomes:** la interfície està en anglès, català i castellà.

[Tornar al README](README.ca.md) · [Readme in English](README.md)

## 1. Tria l'APK adequat

Cada publicació inclou un APK per a cada tipus de processador, més un d'universal:

| APK | Instal·la'l si… |
| --- | --- |
| `app-universal-release.apk` | **No n'estàs segur** — funciona a tots els dispositius compatibles (l'arxiu més gran, ~65 MB) |
| `app-arm64-v8a-release.apk` | Telèfon o tauleta moderns (pràcticament tots els venuts des del 2017: Pixel, Samsung Galaxy, Xiaomi, OnePlus, Motorola, Honor…) |
| `app-armeabi-v7a-release.apk` | Dispositius ARM antics de 32 bits (gama d'entrada de ~2014–2016) |
| `app-x86_64-release.apk` / `app-x86-release.apk` | Emuladors (BlueStacks, Android Studio…) i les poques tauletes x86 |

No saps quin tens? Instal·la l'**universal** i llestos. Si vols precisió, obre un terminal
amb ADB i executa `adb shell getprop ro.product.cpu.abi`, o bé qualsevol aplicació que
mostri la informació del maquinari (per exemple DevCheck).

Tots els APKs contenen exactament la mateixa aplicació; només canvien les llibreries
natives incloses.

## 2. Mètode A — descarregar i instal·lar al telèfon (sense ordinador)

1. Al telèfon, obre la [pàgina de publicacions](https://github.com/elricdog/MycoTrack/releases)
   i descarrega l'APK que triïs (l'última publicació, a dalt de tot).
2. Obre l'arxiu descarregat des de la notificació o des de l'aplicació *Fitxers*.
3. El primer cop, Android avisarà que l'origen és *desconegut*: toca
   **Configuració → Permetre d'aquesta font** (el text varia segons la marca):
   - **Samsung:** *Permetre només amb aquest compte*
   - **Xiaomi / Redmi / POCO:** activa *Instal·lar aplicacions desconegudes* per al navegador i accepta la pantalla d'anàlisi de seguretat
   - **Huawei / Honor:** *Configuració → Seguretat → Més paràmetres → Instal·lar aplicacions desconegudes*
   - **Altres marques:** normalment el sistema mostra un sol interruptor *Permetre* per al navegador o el gestor de fitxers
4. Confirma la instal·lació i obre l'aplicació.
5. Concedeix els permisos que l'aplicació demana en arrencar (veure apartat 4).

> Algunes versions minimalistes d'Android no obren els APKs descarregats per certes
> aplicacions. Si no passa res en tocar l'arxiu, obre'l primer des de l'aplicació
> *Fitxers* (normalment a `Download/`).

## 3. Mètode B — instal·lar amb un ordinador i ADB

Instal·la les [platform-tools](https://developer.android.com/tools/releases/platform-tools)
d'Android a l'ordinador, activa **Configuració → Opcions de desenvolupador → Depuració per
USB** al telèfon (toca set vegades el *Número de muntatge* per fer aparèixer les opcions de
desenvolupador) i connecta per USB:

```bash
adb devices                    # el telèfon ha d'aparèixer com a 'device'
adb install app-universal-release.apk      # o l'APK de l'ABI del teu dispositiu
```

Variants útils:

```bash
adb install -r app-universal-release.apk   # actualitza sense perdre les dades
adb install -r -d app-….apk                # permet rebaixar de versió (proves)
adb shell getprop ro.product.cpu.abi       # quin ABI fa servir el dispositiu
adb uninstall com.mikotrack                # esborra (llegeix abans l'apartat de còpies)
```

## 4. Primer arrencada: permisos

L'aplicació explica cada permisos en pantalla abans de demanar-lo al sistema:

| Permís | Per a què serveix |
| --- | --- |
| Ubicació (*en usar l'aplicació*) | La posició al mapa i l'enregistrament de rutes |
| Ubicació *tot el temps* | L'únic opcional: continua gravant i sonant les alertes amb la pantalla apagada o l'aplicació tancada. Respon *Ara no* si només vols gravar amb la pantalla encesa |
| Notificacions | La notificació d'enregistrament, el registre ràpid 🍄 i les alertes de seguretat |
| Càmera | Les fotografies amb etiquetatge geogràfic fetes des de l'aplicació |

Res no surt del dispositiu: totes les sortides, fotografies i preferències es desen
localment. L'aplicació funciona sense compte i sense serveis de Google Play; Internet només
s'usa per als tessel·les dels mapes, la cerca de llocs i les dades d'altimetria.

## 5. Actualitzar a una versió nova

Descarrega el nou APK i instal·la'l per sobre de l'anterior: sortides, fotografies i
preferències es conserven. No cal desinstal·lar abans — i si ho fas, guarda o exporta
primer les teves dades (*Configuració* i les eines d'exportació). Tots els APKs publicats
estan signats amb la mateixa clau; un APK signat amb una altra clau no s'instal·larà per
sobre de l'aplicació existents.

Pots seguir les publicacions automàticament: a GitHub, *Watch → Custom → Releases* a la
[pàgina del repositori](https://github.com/elricdog/MycoTrack).

## 6. Desinstal·lar

*Configuració → Aplicacions → MycoTrack → Desinstal·lar* esborra l'aplicació i les seves
dades locals. Si vols conservar les sortides i les fotografies, abans fes servir les eines
d'exportació (GPX / GeoJSON / KML / CSV / ZIP i informes PNG) o comparteix la galeria cap
a `Pictures/MikoTrack`.

## Solució de problemes

| Problema | Solució |
| --- | --- |
| *No s'ha instal·lat l'aplicació* o *error d'anàlisi* | La descàrrega està incompleta, o l'APK és per a un altre ABI — torna'l a descarregar o fes servir l'universal |
| La instal·lació es bloqueja durant la descàrrega (Chrome, Xiaomi…) | Toca la descàrrega al panell de notificacions i tria l'opció d'instal·lar igualment, o obre l'arxiu des de l'aplicació Fitxers |
| El mapa no mostra tessel·les | Comprova la connexió a Internet; descarrega àrees offline per Wi-Fi des de *Configuració → Mapes sense connexió* |
| L'enregistrament s'atura amb la pantalla apagada | Concedeix l'ubicació *tot el temps* i desactiva l'optimització de bateria per a l'aplicació (*Configuració → Bateria → MycoTrack → Sense restriccions*) |
| La càmera es queda en negre | Concedeix el permís de Càmera a la configuració del sistema |
| Has instal·lat per error amb una altra signatura (p. ex. un APK de depuració) | Desinstal·la primer l'aplicació (exporta les dades abans) i instal·la l'APK de publicació |
