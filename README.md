# Life VPN

**VPN для Android, который не просит ничего лишнего — теперь ещё и красивый.**
Ни root, ни аккаунта, ни доверия на слово — просто ссылка на подписку и
туннель на движке [Xray-core](https://github.com/XTLS/Xray-core) (VLESS,
Reality, XTLS Vision, транспорт XHTTP). Визуальный редизайн
[Max Strike](https://github.com/stailegrow/maxstrike-vpn-client-android) —
тот же проверенный движок и вся логика подключения, но новый, воздушный
интерфейс: органическая кнопка подключения, живой переливающийся фон и
семь пастельных тем, включая честный чёрный AMOLED. Ставится на телефон
отдельным приложением, рядом с оригиналом, ничего не заменяя.

[English](#english) · [Скачать последний релиз](../../releases/latest) ·
[Сборка из исходников](#сборка-из-исходников)

---

## Что это

Открыл, вставил ссылку подписки, нажал на живое пятно посередине — вот и
всё, что от вас нужно. Дальше Life VPN сам следит за списком серверов
(что добавили на панели провайдера — появится в приложении, что убрали —
пропадёт), сам меряет, какой узел сейчас быстрее, и сам решает, какие
сайты идут через туннель, а какие — мимо (если вы это настроили).

Никакой телеметрии, никакой регистрации, никакого root — само
приложение не знает о вас ничего, что не хранится у вас же на телефоне.
Исходники открыты целиком, можно проверить каждую строчку самостоятельно
или собрать APK своими руками. Устанавливается напрямую, файлом — без
Google Play.

## Что отличает Life VPN от Max Strike

Под капотом — ровно тот же код подключения, что и в оригинале (это
сознательное решение: 2.0 меняет только внешний вид, не логику). Меняется
оболочка:

- органическая кнопка подключения вместо строгой круглой — мягкая
  "капля", которая дышит и покачивается, с ярким подвижным ореолом вокруг
- живой, постоянно переливающийся цветом фон на весь экран — на каждой
  теме, включая тёмные
- семь тем вместо пяти: пастельные Небо, Мята, Лаванда, Персик, Роза,
  плюс мягкая тёмная Ночь и честный чёрный AMOLED (кнопка на нём тоже
  чёрная — экономия батареи и никакого лишнего света в темноте)
- фирменный знак — органическая капля с разрывом — в шапке приложения, на
  экране запуска и в самой иконке
- отдельный `applicationId` (`com.stailegrow.lifevpn`), поэтому Life VPN
  ставится на телефон рядом с Max Strike, а не поверх него

## Возможности

**Подключение**

- VLESS поверх TCP с Reality и XTLS Vision, а также транспорт XHTTP
- маршрутизация трафика: два готовых пресета («глобально» и «обход РФ»)
  плюс свой список доменов, которые всегда идут напрямую
- раздельный DNS: домашние имена резолвит местный DNS, внешние — через туннель
- обход локальной сети: роутер, принтеры и NAS остаются доступны при
  включённом VPN
- раздельное туннелирование — выбранные приложения работают в обход VPN

**Серверы и подписки**

- добавление узла ссылкой (по одной или пачкой), подпиской по URL или
  сканированием QR-кода камерой
- автообновление списка узлов по подписке, переименование, удаление

**Измерения**

- задержка меряется через туннель — тем же путём, которым пойдёт трафик;
  опрашивается сама каждые 10 секунд, пока открыта вкладка «Серверы»
- замер скорости внутри клиента

**Интерфейс**

- семь цветовых тем, живой переливающийся фон, органическая анимированная
  кнопка подключения
- русский и английский язык, переключается на месте, без перезапуска

## Требования

- Android 7.0 (API 24) или новее
- для сборки — Android Studio (текущая стабильная версия) и Android NDK

## Установка

Собранный релизный APK ставится обычным сайдлодом: скачать файл и
разрешить системе «Установить из неизвестных источников» при первом
запуске установки (диалог покажет сама система).

## Сборка из исходников

```bash
git clone https://github.com/stailegrow/lifevpn-android.git
cd lifevpn-android
./Scripts/build-libxray.sh   # соберёт libXray.aar (нужны git, go, NDK, JDK)
```

Дальше открыть папку в Android Studio (`File → Open`) и синкнуть проект —
она сама подтянет остальные зависимости.

Релизная сборка подписывается ключом из `keystore.properties` в корне
проекта — этот файл и сам keystore не входят в репозиторий (см.
`.gitignore`), у каждого, кто собирает проект сам, ключ будет свой:

```bash
keytool -genkeypair -v -keystore keystore/release.keystore \
  -alias lifevpn -keyalg RSA -keysize 2048 -validity 10000
```

и рядом `keystore.properties`:

```
storeFile=keystore/release.keystore
storePassword=...
keyAlias=lifevpn
keyPassword=...
```

Без этого файла release-сборка просто останется неподписанной — соберётся,
но не установится без ручной подписи.

## Приватность

Приложение не собирает статистику и никуда не отправляет данные о
пользователе. Ссылки подписок, настройки и список узлов хранятся только на
этом телефоне. Единственные сетевые запросы, которые клиент делает от
себя, — обновление подписки, загрузка баз маршрутизации (geosite/geoip),
проверка внешнего адреса и сами измерения (задержка, скорость).

## Лицензия

MIT — см. [LICENSE](LICENSE).

Ядро [Xray-core](https://github.com/XTLS/Xray-core) поставляется отдельным
образом (gomobile-обёртка `libXray`) и распространяется под собственной
лицензией (MPL-2.0).

---

## English

**A VPN for Android that doesn't ask for anything extra — now with a
proper coat of paint.** No root, no account, no need to take anyone's
word for it — just a subscription link and a tunnel built on
[Xray-core](https://github.com/XTLS/Xray-core) (VLESS, Reality, XTLS
Vision, the XHTTP transport). A visual redesign of
[Max Strike](https://github.com/stailegrow/maxstrike-vpn-client-android) —
same proven connection engine, same logic, but a new, airy interface: an
organic connect button, a living animated background, and seven pastel
themes, including a proper black AMOLED mode. Installs as a separate app
alongside the original, replacing nothing.

Open it, paste your subscription link, tap the living blob in the middle
— that's the whole setup. From there Life VPN keeps its own server list
in sync with your subscription, measures which node is fastest right
now, and — if you've set it up that way — decides which sites go through
the tunnel and which don't.

No telemetry, no sign-up, no root. The source is fully open, so you can
check every line yourself or build the APK by hand. Distributed as a
plain APK file, not through Google Play.

**What's different from Max Strike** — same connection code underneath
(2.0 is a cosmetic redesign, not a rewrite of the logic): an organic
connect button that breathes and sways with a bright moving glow; a
living, colour-shifting background on every screen and theme; seven
themes instead of five (five pastels, a soft dark Night, and a true-black
AMOLED where the button itself goes black too); a matching brand mark —
an organic drop with a circular gap — in the header, the launch screen,
and the app icon; and its own `applicationId`
(`com.stailegrow.lifevpn`), so it installs next to Max Strike rather than
over it.

**Highlights**

- traffic routing — two ready-made presets (global / bypass-RU) plus your own
  always-direct domain list; split DNS; local-network bypass; per-app split
  tunnelling
- subscriptions by link, in bulk, or by scanning a QR code with the camera
- latency measured *through* the tunnel — the path traffic actually takes —
  polled every ten seconds while the Servers tab is open
- in-app speed test
- seven colour themes, a living background, an organic animated connect
  button, Russian and English interface, switchable on the spot

**Requirements** — Android 7.0 (API 24) or newer. Building needs Android
Studio and the Android NDK.

**Install** — sideload the release APK and allow "install from unknown
sources" when the system prompts for it.

**Build**

```bash
git clone https://github.com/stailegrow/lifevpn-android.git
cd lifevpn-android
./Scripts/build-libxray.sh   # builds libXray.aar — needs git, go, NDK, JDK
```

Then open the folder in Android Studio and let it sync. The release build is
signed with the key referenced by `keystore.properties` at the project root;
neither the file nor the keystore ship in the repository (see
`.gitignore`) — generate your own with `keytool -genkeypair` and point
`keystore.properties` at it, or the release build will simply come out
unsigned.

**Privacy** — no telemetry, no accounts, nothing leaves the phone except the
subscription refresh, routing databases, an external-IP probe, and the
latency/speed measurements themselves.

**Licence** — MIT. Xray-core ships as a separate gomobile build (`libXray`)
under its own licence (MPL-2.0).
