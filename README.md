<h1 align="center">Life VPN для Android</h1>

<p align="center">
  <b>VPN, который хочется открывать.</b><br>
  Одна живая кнопка, быстрые узлы и аккуратный интерфейс — без аккаунтов, root и лишних экранов.
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/stailegrow/lifevpn-android?style=for-the-badge&label=%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D1%8F&color=f97316" alt="Последняя версия"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-1c1714?style=for-the-badge&logo=android&logoColor=white" alt="Android 7.0 и новее">
  <img src="https://img.shields.io/badge/Xray--core-26.10-1c1714?style=for-the-badge" alt="Ядро Xray-core 26.10">
</p>

<p align="center">
  <a href="../../releases/latest"><b>⬇ Скачать APK</b></a>
  &nbsp;·&nbsp; <a href="#установка">Установка</a>
  &nbsp;·&nbsp; <a href="#english">English</a>
</p>

---

## Одна кнопка — и вы под защитой

Вставили ссылку на подписку, нажали на живое пятно посередине экрана — оно
дышит, переливается и вспыхивает искрами, когда туннель поднят. Маршруты,
DNS и выбор приложений Life VPN настраивает сам.

## Ваш цвет

Основная тема **Life** — тёплый почти чёрный фон и оранжевый акцент, в одном
стиле с macOS-версией Life VPN. Рядом — пастельные Небо, Мята, Лаванда,
Персик и Роза, мягкая тёмная Ночь и честный чёрный AMOLED, где даже кнопка
в покое чёрная. Живой фон переливается оттенками выбранной темы.

## Что внутри

**Подключение**
- VLESS с Reality и XTLS Vision, транспорт XHTTP — на свежем ядре Xray-core
- маршрутизация «Обход РФ»: российские сервисы напрямую, остальное через туннель
- раздельный DNS, обход локальной сети, свой список доменов «всегда напрямую»
- раздельное туннелирование: выбранные приложения работают мимо VPN
- плитка в шторке быстрых настроек — включить VPN, не открывая приложение

**Подписки**
- добавление ссылкой, пачкой ссылок, подпиской по URL или QR-кодом с камеры
- список узлов обновляется сам: новые узлы на панели появляются в приложении, удалённые исчезают

**Измерения**
- задержка меряется напрямую от телефона до узла — честная цифра, подключены вы или нет
- замер скорости по трём независимым источникам

**Удобство**
- обновления приходят сами: приложение проверяет новые версии на GitHub, скачивает и ставит их в пару нажатий
- русский и английский язык, переключаются на месте, без перезапуска

## Установка

1. Скачайте `LifeVPN-<версия>.apk` со страницы [последнего релиза](../../releases/latest).
2. Откройте файл на телефоне. При первой установке Android попросит разрешить
   установку из этого источника — разрешите.

Дальше Life VPN обновляется сам: когда выходит новая версия, на главном экране
появляется баннер, а в «Настройках» — кнопка «Скачать».

**Требования:** Android 7.0 или новее, 64-битный процессор (arm64).

## Приватность

Никакой аналитики, телеметрии и учётных записей. Подписки, настройки и список
узлов хранятся только на телефоне. Само приложение обращается только туда, где
без этого не обойтись:

- к серверу вашей подписки — чтобы обновить список узлов;
- к самим узлам — чтобы измерить задержку;
- к CDN jsDelivr — за базами правил маршрутизации;
- к GitHub — чтобы проверить, вышла ли новая версия;
- к сервисам определения внешнего адреса — чтобы убедиться, что туннель работает;
- к Cloudflare, Hetzner и CacheFly — только когда вы сами запускаете замер скорости.

## Лицензия

Life VPN — проприетарный продукт, все права защищены. Код открыт **только для
просмотра**. Копировать, изменять, собирать, распространять, декомпилировать и
создавать форки для использования запрещено, как и копировать дизайн.
Подробно — в [LICENSE](LICENSE), условия для пользователей приложения — в
[EULA.md](EULA.md).

Ядро [Xray-core](https://github.com/XTLS/Xray-core) — отдельная программа под
лицензией MPL-2.0; остальные сторонние компоненты перечислены в конце
[LICENSE](LICENSE).

---

<a id="english"></a>

## English

**Life VPN is an Android VPN client you'll actually enjoy opening.** One living
button, fast nodes and a clean interface — no accounts, no root.

- **One-tap connect.** Paste a subscription link and tap the living blob: it
  breathes, shimmers and sparkles once the tunnel is up.
- **VLESS with Reality, XTLS Vision and XHTTP** on a current Xray-core.
- **Smart routing**: bypass-RU preset, split DNS, LAN bypass, your own
  always-direct domains and per-app split tunnelling.
- **Subscriptions** by link, in bulk, by URL or by camera QR, kept in sync
  automatically.
- **Honest latency**, measured straight from your phone to each node, and a
  speed test across three sources.
- **Quick Settings tile** to toggle the VPN without opening the app.
- **Themes**: the signature warm-dark Life theme, five pastels, a soft Night
  and a true-black AMOLED, all with a living background.
- **Automatic updates** from GitHub releases, installed in a couple of taps.

**Install:** download the APK from the
[latest release](../../releases/latest), open it on your phone and allow
installs from that source when Android asks. Requires Android 7.0+ (arm64).

**Privacy:** no analytics, no telemetry, no accounts. Data stays on your phone.

**Licence:** proprietary, all rights reserved. The source is published for
viewing only. Copying, modifying, building, distributing, decompiling, using
forks and copying the design are prohibited. See [LICENSE](LICENSE) and
[EULA.md](EULA.md).
