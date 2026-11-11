package app.familyjourney.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.DecorArt
import app.familyjourney.core.ui.ForestStorageButton
import app.familyjourney.core.ui.ForestStoragePanel
import app.familyjourney.core.ui.StorageEntry
import app.familyjourney.core.ui.TreeSpecies
import app.familyjourney.feature.forest.DecorShop
import app.familyjourney.feature.forest.EditableMapScene
import app.familyjourney.feature.forest.SceneUi
import org.junit.Rule
import org.junit.Test

/**
 * 폰·태블릿 앱(`phone/`, 결정 #37)에서 그대로 부르는 숲 부품 캡처(차 앱 화면과 별개).
 * 폰 세로 400×860, 태블릿 가로 1280×800. 부품은 차·주행 상태를 모른다(부르는 쪽이 주차·연결 상태를 정한다).
 */
class PhoneForestShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    private val phone = 400 to 860
    private val tablet = 1280 to 800

    /** 숲 맵(끌어 옮기기·보관함 버튼). 폰은 칸이 32dp보다 작으면 크게 보기로 시작(minCellDp), 세로 높이를 채움(fillHeight) */
    @Test
    fun forestMap() {
        val shown = ShotSamples.forest().shown!!
        val scene = SceneUi(shown.planted, shown.sprouts, shown.animals, 0, shown.map, shown.spots, shown.decorStock)
        for ((name, size) in listOf("phone" to phone, "tablet" to tablet)) {
            paparazzi.shoot("phone-forest-map-$name", size.first, size.second, settled = true) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    EditableMapScene(scene, Modifier.fillMaxSize(), minCellDp = 32.dp, fillHeight = name == "phone") {}
                }
            }
        }
    }

    /** 상점 본문(DecorShop): 폰은 카드 2칸·그림 88dp, 칩은 줄 바꿈 */
    @Test
    fun shop() {
        val shown = ShotSamples.forest().shown!!
        for ((name, size) in listOf("phone" to phone, "tablet" to tablet)) {
            paparazzi.shoot("phone-shop-$name", size.first, size.second, settled = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DecorShop(shown.shop, shown.shopCategories, onBuy = {}, onMessage = {}, stateKey = shown.childId)
                }
            }
        }
    }

    /** 보관함(나무 묶음·소품 ×개수): 폰 폭에서 2칸 */
    @Test
    fun storage() {
        val entries = listOf(StorageEntry.Trees(TreeSpecies.T01, 3)) +
            listOfNotNull(DecorArt.ofCode("WOOD-FENCE-HORIZONTAL")?.let { StorageEntry.Decor(it, 8) }) +
            listOfNotNull(DecorArt.ofCode("WOODEN-BENCH")?.let { StorageEntry.Decor(it, 1) }) +
            listOfNotNull(DecorArt.ofCode("SAFE-CAMPFIRE")?.let { StorageEntry.Decor(it, 2) })
        paparazzi.shoot("phone-storage-phone", phone.first, phone.second, settled = true) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ForestStoragePanel(entries = entries, message = null, onTakeOut = {}, onClose = {})
                    ForestStorageButton(total = entries.sumOf { it.count }, highlighted = false, onClick = {})
                }
            }
        }
    }
}
