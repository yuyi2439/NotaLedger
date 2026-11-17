package cn.yausen.notaledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Face3
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon set offered for categories.
 *
 * Categories store the numeric [id], never the icon name, so the artwork behind a
 * key can be swapped later without rewriting any stored category. Every entry was
 * checked to exist in the pinned icon artifact rather than assumed.
 */
object CategoryIcons {

    private data class Entry(val id: Int, val icon: ImageVector, val label: String)

    private val entries: List<Entry> = listOf(
        Entry(1, Icons.Filled.Restaurant, "餐饮"),
        Entry(2, Icons.Filled.Fastfood, "快餐"),
        Entry(3, Icons.Filled.LunchDining, "正餐"),
        Entry(4, Icons.Filled.LocalCafe, "咖啡"),
        Entry(5, Icons.Filled.RamenDining, "面食"),
        Entry(6, Icons.Filled.Icecream, "甜品"),
        Entry(7, Icons.Filled.WineBar, "酒水"),
        Entry(8, Icons.Filled.LocalBar, "酒吧"),
        Entry(10, Icons.Filled.DirectionsBus, "公交"),
        Entry(11, Icons.Filled.DirectionsSubway, "地铁"),
        Entry(12, Icons.Filled.DirectionsCar, "打车"),
        Entry(13, Icons.Filled.LocalTaxi, "出租车"),
        Entry(14, Icons.Filled.Train, "火车"),
        Entry(15, Icons.Filled.Flight, "机票"),
        Entry(16, Icons.Filled.TwoWheeler, "摩托"),
        Entry(17, Icons.Filled.ElectricBike, "电动车"),
        Entry(18, Icons.Filled.LocalParking, "停车"),
        Entry(20, Icons.Filled.ShoppingBag, "购物"),
        Entry(21, Icons.Filled.LocalMall, "商场"),
        Entry(22, Icons.Filled.Storefront, "小店"),
        Entry(23, Icons.Filled.Checkroom, "服饰"),
        Entry(24, Icons.Filled.LocalFlorist, "鲜花"),
        Entry(30, Icons.Filled.House, "居家"),
        Entry(31, Icons.Filled.Apartment, "房租"),
        Entry(32, Icons.Filled.BuildCircle, "维修"),
        Entry(33, Icons.Filled.LocalLaundryService, "洗衣"),
        Entry(40, Icons.Filled.Movie, "电影"),
        Entry(41, Icons.Filled.Theaters, "演出"),
        Entry(42, Icons.Filled.SportsEsports, "游戏"),
        Entry(43, Icons.Filled.VideogameAsset, "电玩"),
        Entry(44, Icons.Filled.Headphones, "音乐"),
        Entry(45, Icons.Filled.MusicNote, "乐器"),
        Entry(46, Icons.Filled.SportsBasketball, "运动"),
        Entry(47, Icons.Filled.SportsSoccer, "球赛"),
        Entry(48, Icons.Filled.FitnessCenter, "健身"),
        Entry(49, Icons.Filled.SelfImprovement, "养生"),
        Entry(50, Icons.Filled.CameraAlt, "摄影"),
        Entry(51, Icons.Filled.Palette, "兴趣"),
        Entry(60, Icons.Filled.MedicalServices, "医疗"),
        Entry(61, Icons.Filled.LocalPharmacy, "药品"),
        Entry(62, Icons.Filled.Healing, "看病"),
        Entry(63, Icons.Filled.Face3, "美容"),
        Entry(70, Icons.AutoMirrored.Filled.MenuBook, "学习"),
        Entry(71, Icons.Filled.School, "学费"),
        Entry(72, Icons.Filled.Bookmark, "书籍"),
        Entry(80, Icons.Filled.Smartphone, "手机"),
        Entry(81, Icons.Filled.PhoneAndroid, "话费"),
        Entry(82, Icons.Filled.SimCard, "流量"),
        Entry(83, Icons.Filled.Wifi, "宽带"),
        Entry(90, Icons.Filled.Work, "工作"),
        Entry(91, Icons.Filled.BusinessCenter, "兼职"),
        Entry(92, Icons.Filled.EmojiEvents, "奖金"),
        Entry(93, Icons.AutoMirrored.Filled.TrendingUp, "理财"),
        Entry(94, Icons.Filled.Savings, "储蓄"),
        Entry(95, Icons.Filled.LocalAtm, "现金"),
        Entry(96, Icons.Filled.Payments, "收款"),
        Entry(97, Icons.Filled.AccountBalance, "银行"),
        Entry(98, Icons.Filled.AccountBalanceWallet, "钱包"),
        Entry(99, Icons.Filled.CreditCard, "信用卡"),
        Entry(100, Icons.Filled.Grade, "红包"),
        Entry(101, Icons.Filled.Redeem, "礼品"),
        Entry(102, Icons.Filled.CardGiftcard, "人情"),
        Entry(103, Icons.Filled.Pets, "宠物"),
        Entry(104, Icons.Filled.ChildCare, "孩子"),
        Entry(105, Icons.Filled.ContentCut, "其他"),
    )

    private val byId: Map<Int, ImageVector> = entries.associate { it.id to it.icon }

    /** Ids in the order they are offered in the picker. */
    val allIds: List<Int> = entries.map { it.id }

    /** One built-in icon together with the name it stands for. */
    data class Preset(val id: Int, val name: String)

    /**
     * The built-in set a category can be made from: an icon and a name kept
     * together, so choosing one already describes a category. Each preset works as
     * a top-level category or as a sub-category, and the user may rename it or swap
     * the icon afterwards.
     */
    val presets: List<Preset> = entries.map { Preset(it.id, it.label) }

    /** The name that belongs to an icon id; empty when the id is unknown. */
    fun nameOf(id: Int): String = entries.firstOrNull { it.id == id }?.label.orEmpty()

    /** Seeds reference icons by name; these ids are the contract with stored data. */
    const val RESTAURANT = 1
    const val DIRECTIONS_BUS = 10
    const val SHOPPING_BAG = 20
    const val HOUSE = 30
    const val MOVIE = 40
    const val MEDICAL_SERVICES = 60
    const val MENU_BOOK = 70
    const val SMARTPHONE = 80
    const val WORK = 90
    const val BUSINESS_CENTER = 91
    const val EMOJI_EVENTS = 92
    const val SAVINGS = 94
    const val LOCAL_ATM = 95
    const val CARD_GIFTCARD = 102
    const val REDEEM = 101
    const val CONTENT_CUT = 105

    private val fallback = Icons.Filled.ContentCut

    /**
     * Never throws: an id written by a newer version of the app still renders.
     */
    fun vectorAt(id: Int): ImageVector = byId[id] ?: fallback

    /** Starting point for a brand new category. */
    fun defaultFor(kind: Int): Int = if (kind == 1) WORK else RESTAURANT
}
