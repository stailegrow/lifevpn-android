package com.stailegrow.maxstrike.vpn

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.stailegrow.maxstrike.MainActivity
import com.stailegrow.maxstrike.R
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.model.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Плитка "Быстрых настроек" (шторка уведомлений) — по прямой просьбе
 * пользователя, по образцу тумблера VPN у других клиентов на телефоне.
 * TileService — системный API (android.service.quicksettings, доступен с
 * API 24 — это и есть наш minSdk), никакой отдельной логики подключения
 * тут нет: плитка просто дёргает ту же ConnectionManager, что и большая
 * кнопка на главном экране (ConnectSlab/HomeScreen).
 *
 * Все Store (ServerStore, ConnectionManager и т.д.) в этот момент уже
 * гарантированно инициализированы — Android всегда вызывает
 * Application.onCreate() (там и живёт вся эта инициализация, см.
 * MaxStrikeApplication.kt) раньше первого компонента процесса, включая
 * TileService, даже если MainActivity ни разу не запускался с момента
 * перезагрузки телефона. Поэтому плитка работает как полноценный тумблер
 * сама по себе, без необходимости открывать приложение — открытие
 * приложения нужно только в двух редких случаях, см. onClick().
 *
 * Какой сервер подключать/отключать — та же логика выбора, что у
 * HomeScreen.slabServer: если сейчас что-то активно (подключено или в
 * процессе подключения) — это активный сервер (тап отключает именно
 * его); иначе — тот сервер, что выбран в списке (тап подключает его).
 */
class VpnQuickTile : TileService() {

    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = CoroutineScope(Dispatchers.Main.immediate + Job()).launch {
            combine(
                ConnectionManager.state,
                ConnectionManager.activeServer,
                ServerStore.selectedID,
                ServerStore.servers,
            ) { state, active, selectedID, servers ->
                state to (active ?: servers.firstOrNull { it.id == selectedID })
            }.collect { (state, server) -> render(state, server) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    private fun render(state: ConnectionState, server: ProxyConfig?) {
        val tile = qsTile ?: return
        tile.label = "Life VPN"

        val subtitle: String
        // Своя иконка вместо системного замка — тот же силуэт, что на
        // лаунчере/сплэше/AppMark в TopBar, залитый фирменным зелёным
        // "good" (#8FD9BE) именно когда VPN подключён — по прямой просьбе
        // пользователя. Задаём tile.icon явно на каждом render(), а не
        // полагаемся на статичный android:icon из манифеста (тот годится
        // только на самый первый показ до onStartListening()) — иначе на
        // части прошивок system tint перекрасит иконку в свой служебный
        // цвет вместо нашего зелёного.
        val connected = state is ConnectionState.Connected
        tile.icon = Icon.createWithResource(
            this,
            if (connected) R.drawable.ic_tile_on else R.drawable.ic_tile_off,
        )
        when (state) {
            is ConnectionState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                subtitle = server?.displayName ?: L.t("Подключено", "Connected")
            }
            is ConnectionState.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                subtitle = L.t("Подключение…", "Connecting…")
            }
            is ConnectionState.Failed -> {
                tile.state = Tile.STATE_INACTIVE
                subtitle = server?.displayName ?: L.t("Ошибка", "Error")
            }
            ConnectionState.Disconnected -> {
                tile.state = Tile.STATE_INACTIVE
                subtitle = server?.displayName ?: L.t("Отключено", "Disconnected")
            }
        }
        // Tile.subtitle — API 29+ (Q), нашего minSdk 24 не хватает, метод
        // просто не существует в фреймворке на более старых версиях —
        // без проверки версии тут будет падать на Android 8-9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val state = ConnectionManager.state.value

        // Уже что-то активно (подключено или подключается) — тап всегда
        // отключает, независимо от того, какой сервер сейчас выбран в
        // списке (точно так же ведёт себя ConnectSlab на главном экране).
        if (state.isConnected || state.isBusy) {
            ConnectionManager.disconnect(this)
            return
        }

        val server = ServerStore.servers.value.firstOrNull { it.id == ServerStore.selectedID.value }
        if (server == null) {
            // Список серверов почему-то пуст (например, самый первый запуск
            // без единого добавленного сервера) — открываем приложение
            // вместо того, чтобы тихо ничего не делать по тапу на плитку.
            openApp()
            return
        }

        if (ConnectionManager.needsPermission(this)) {
            // Системный диалог "разрешить VPN" может показать только
            // Activity — плитке показать его нечем, поэтому открываем
            // MainActivity и просим её провести пользователя через этот
            // диалог и подключиться, как будто он тапнул по большой
            // кнопке сам (см. MainActivity.handleTileIntent()).
            openApp(autoConnectServerID = server.id)
            return
        }

        ConnectionManager.connect(this, server)
    }

    private fun openApp(autoConnectServerID: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (autoConnectServerID != null) {
                action = MainActivity.ACTION_TILE_CONNECT
                putExtra(MainActivity.EXTRA_TILE_SERVER_ID, autoConnectServerID)
            }
        }
        // startActivityAndCollapse(Intent) объявлен deprecated в API 34
        // (UPSIDE_DOWN_CAKE) в пользу варианта с PendingIntent — наш
        // minSdk 24 требует поддерживать оба пути.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
