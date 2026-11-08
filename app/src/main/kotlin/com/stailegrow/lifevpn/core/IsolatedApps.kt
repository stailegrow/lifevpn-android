package com.stailegrow.lifevpn.core

import android.content.Context
import android.content.pm.PackageManager
import com.stailegrow.lifevpn.model.RoutingConfig

/**
 * Изоляция российских приложений в пресете «Обход РФ».
 *
 * Такие приложения (в первую очередь мессенджер MAX) проверяют внешний IP
 * через несколько сервисов и сравнивают результаты — так находят личный
 * VPN-сервер и связывают его адрес с человеком. Если их трафик идёт мимо
 * туннеля (VpnService.Builder.addDisallowedApplication), они видят только
 * обычный адрес провайдера и не узнают адрес сервера: их соединения вообще
 * не попадают в TUN и в ядро Xray.
 *
 * Это не прячет сам факт VPN полностью: Android может показывать приложению
 * интерфейс tun0. Цель — не дать узнать и «засветить» адрес сервера.
 *
 * Включено по умолчанию и действует только в «Обходе РФ»: в «Глобально»
 * пользователь сознательно хочет, чтобы весь трафик шёл через VPN.
 * Пакеты, которых нет на телефоне, просто пропускаются.
 */
object IsolatedApps {

    data class App(val packageName: String, val title: String)

    val known: List<App> = listOf(
        App("ru.oneme.app", "MAX"),
        App("com.vkontakte.android", "ВКонтакте"),
        App("com.vk.vkvideo", "VK Видео"),
        App("ru.ok.android", "Одноклассники"),
        App("ru.mail.mailapp", "Почта Mail"),
        App("ru.vk.store", "RuStore"),
        App("ru.rostel", "Госуслуги"),
        App("ru.sberbankmobile", "СберБанк Онлайн"),
        App("com.idamob.tinkoff.android", "Т-Банк"),
        App("ru.vtb24.mobilebanking.android", "ВТБ Онлайн"),
        App("ru.alfabank.mobile.android", "Альфа-Банк"),
        App("ru.ozon.app.android", "Ozon"),
        App("com.wildberries.ru", "Wildberries"),
        App("ru.yandex.searchplugin", "Яндекс с Алисой"),
        App("ru.yandex.taxi", "Yandex Go"),
        App("ru.yandex.yandexmaps", "Яндекс Карты"),
        App("ru.dublgis.dgismobile", "2ГИС"),
        App("ru.mts.mymts", "Мой МТС"),
        App("ru.megafon.mlk", "МегаФон"),
        App("ru.beeline.services", "Билайн"),
        App("ru.tele2.mytele2", "Т2"),
    )

    /** Действует ли изоляция для этого конфига маршрутизации. */
    fun appliesTo(routing: RoutingConfig, enabled: Boolean): Boolean =
        enabled && routing.presetID == "bypass-ru"

    /** Какие из известных приложений реально установлены. */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        return known.filter { app ->
            try {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(app.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
