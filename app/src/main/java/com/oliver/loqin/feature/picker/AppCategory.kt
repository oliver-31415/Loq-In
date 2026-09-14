/*
 * Loq In
 * Copyright (C) 2026 Loq In Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.oliver.loqin.feature.picker

import android.content.pm.ApplicationInfo
import com.oliver.loqin.blocking.isBrowserPackage

/**
 * Coarse buckets for the app picker filter chips.
 * Precedence: browsers first, then a curated map of well-known packages
 * (system categories are sparsely declared), then [ApplicationInfo.category].
 */
enum class AppCategory {
    SOCIAL,
    MEDIA,
    GAMES,
    BROWSERS,
    SHOP_PAY,
    OTHER;

    companion object {
        private val CURATED: Map<String, AppCategory> = buildMap {
            fun social(vararg pkgs: String) = pkgs.forEach { put(it, SOCIAL) }
            fun media(vararg pkgs: String) = pkgs.forEach { put(it, MEDIA) }
            fun games(vararg pkgs: String) = pkgs.forEach { put(it, GAMES) }
            fun shopPay(vararg pkgs: String) = pkgs.forEach { put(it, SHOP_PAY) }

            social(
                "com.facebook.katana", "com.facebook.lite", "com.facebook.orca",
                "com.instagram.android", "com.instagram.barcelona",
                "com.twitter.android", "com.snapchat.android",
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.reddit.frontpage", "com.discord", "org.telegram.messenger",
                "org.telegram.plus", "com.whatsapp", "com.whatsapp.w4b",
                "com.linkedin.android", "com.pinterest",
                "app.bsky.social", "com.keylesspalace.tusky", "org.mastodon",
                "com.viber.voip", "org.thoughtcrime.securesms", "com.linecorp.line",
                "com.tencent.mm", "jp.naver.line", "com.skype.raider",
                "com.tumblr", "com.vk.android", "com.bereal.ft",
            )
            media(
                "com.google.android.youtube", "app.revanced.android.youtube",
                "app.morphe.android.youtube", "com.google.android.apps.youtube.music",
                "com.google.android.youtube.tv", "com.netflix.mediaclient",
                "com.spotify.music", "tv.twitch.android.app",
                "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
                "com.hulu.plus", "com.wbd.stream", "com.paramountplus",
                "com.peacocktv.peacockandroid", "com.plexapp.android",
                "org.videolan.vlc", "com.mxtech.videoplayer.ad",
                "com.apple.android.music", "deezer.android.app",
                "com.soundcloud.android", "com.google.android.apps.photos",
                "com.vsco.cam", "com.adobe.lrmobile", "com.picsart.studio",
            )
            games(
                "com.king.candycrushsaga", "com.roblox.client",
                "com.supercell.clashofclans", "com.supercell.clashroyale",
                "com.supercell.brawlstars", "com.supercell.boombeach",
                "com.supercell.squadbusters", "com.innersloth.spacemarine",
                "com.tencent.ig", "com.activision.callofduty.shooter",
                "com.miHoYo.GenshinImpact", "com.hoyoverse.hkrpgoversea",
                "com.mojang.minecraftpe", "com.kiloo.subwaysurf",
                "com.nianticlabs.pokemongo", "com.ea.gp.fifamobile",
                "com.zynga.words", "com.playrix.township",
            )
            shopPay(
                "com.amazon.mShop.android.shopping", "com.amazon.windowshop",
                "com.ebay.mobile", "com.alibaba.aliexpresshd",
                "com.zzkko", "com.shein.android", "com.temu.app",
                "com.zalando.mobile", "com.etsy.android", "com.contextlogic.wish",
                "com.paypal.android.p2pmobile", "com.revolut.revolut",
                "com.transferwise.android", "com.google.android.apps.walletnfcrel",
                "com.samsung.android.spay", "com.squareup.cash",
                "com.venmo", "com.afterpaymobile", "com.klarna.shopping",
                "com.chase.sig.android", "com.bankofamerica.cashpro",
                "com.citi.citimobile", "com.wf.wellsfargo",
                "com.hsbc.hsbcnetmobile", "com.barclays.android.barclaysmobilebanking",
                "com.santander.app", "com.bnpparibas.hellobank",
                "com.ing.banking", "com.n26.mobile",
                "com.westpac.bank", "com.up.money", "com.commbank.netbank",
                "com.anz.android.goMoney", "com.nab.nab",
            )
        }

        fun categoryFor(packageName: String, systemCategory: Int): AppCategory {
            if (isBrowserPackage(packageName)) return BROWSERS
            CURATED[packageName]?.let { return it }
            return when (systemCategory) {
                ApplicationInfo.CATEGORY_GAME -> GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_IMAGE -> MEDIA
                else -> OTHER
            }
        }
    }
}
