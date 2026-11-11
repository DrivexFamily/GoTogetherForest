package app.familyjourney.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/*
 * 미리보기 크기 (dp). Pleos 에뮬레이터 화면은 2560×1440 px이지만 밀도(dp)는 아직 확인 전이다.
 * 아래는 밀도 2.0(1280×720dp)에서 왼쪽 Driving view를 뺀 앱 영역을 약 1200dp로 가정한 값이다.
 * 실제 값을 확인하면 이 숫자만 고친다. 컴포넌트는 받은 자리로 판단하므로 규칙은 그대로다.
 *
 *  - 전체 앱 영역     1200×720  (Expanded)
 *  - 반 화면 1:1       600×720  (Medium)   ← 기본 사용처: 내비 | 같이가숲
 *  - 2:1의 넓은 쪽     800×720  (Medium)
 *  - 2:1의 좁은 쪽     400×720  (Compact)
 *  - 세로 전체         720×1200 (Medium, 길이 구불구불)
 *  - 세로 반           720×600  (Medium)
 *  - 낮은 가로         960×440  (Expanded + Short)
 */

@Preview(name = "1 전체 앱 영역 1200x720", widthDp = 1200, heightDp = 720, showBackground = true)
@Preview(name = "2 반 화면 1:1 600x720", widthDp = 600, heightDp = 720, showBackground = true)
@Preview(name = "3 2:1 넓은 쪽 800x720", widthDp = 800, heightDp = 720, showBackground = true)
@Preview(name = "4 2:1 좁은 쪽 400x720", widthDp = 400, heightDp = 720, showBackground = true)
@Preview(name = "5 세로 전체 720x1200", widthDp = 720, heightDp = 1200, showBackground = true)
@Preview(name = "6 세로 반 720x600", widthDp = 720, heightDp = 600, showBackground = true)
@Preview(name = "7 낮은 가로 960x440", widthDp = 960, heightDp = 440, showBackground = true)
annotation class GgfWindowPreviews

@Preview(name = "다크 · 전체 1200x720", widthDp = 1200, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "다크 · 반 화면 600x720", widthDp = 600, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "다크 · 좁은 쪽 400x720", widthDp = 400, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "다크 · 세로 720x1200", widthDp = 720, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class GgfWindowPreviewsDark

@Preview(name = "라이트", showBackground = true)
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class GgfThemePreviews

@Composable
private fun SizeButton() {
    OutlinedButton(onClick = {}, modifier = Modifier.height(64.dp)) { Text("크게", style = GgfTheme.type.label) }
}

@GgfWindowPreviews
@Composable
private fun KidProgressPanelDrivingPreview() = GgfTheme {
    KidProgressPanel(
        percent = 40,
        sentence = "끝말잇기 4판 하면 도착해요",
        driving = true,
        demo = true,
        sentenceIcon = KidSentenceIcon.WordChain,
        detail = "약 40분 남음",
        actions = { SizeButton() },
    )
}

@GgfWindowPreviewsDark
@Composable
private fun KidProgressPanelParkedDarkPreview() = GgfTheme {
    KidProgressPanel(
        percent = 75,
        sentence = "노래 세 곡 들으면 도착해요",
        driving = false,
        demo = true,
        sentenceIcon = KidSentenceIcon.Song,
        detail = "약 12분 남음",
        actions = { SizeButton() },
    )
}

@GgfThemePreviews
@Composable
private fun ProgressRoadStraightPreview() = GgfTheme {
    Column(Modifier.background(GgfTheme.colors.paper).padding(16.dp).width(900.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (p in listOf(0, 25, 50, 75, 100)) ProgressRoad(percent = p, animate = false, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "구불구불한 길 400x720", widthDp = 400, heightDp = 720, showBackground = true)
@Preview(name = "구불구불한 길 · 다크", widthDp = 400, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ProgressRoadWindingPreview() = GgfTheme {
    Box(Modifier.fillMaxSize().background(GgfTheme.colors.paper).padding(16.dp)) {
        ProgressRoad(percent = 62, animate = false, modifier = Modifier.fillMaxSize())
    }
}

@GgfThemePreviews
@Composable
private fun FamilyCharacterPosesPreview() = GgfTheme {
    Column(Modifier.background(GgfTheme.colors.sceneSky).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (pose in FamilyPose.entries.filter { it.driving }) FamilyCharacter(pose, animate = false, modifier = Modifier.width(220.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (pose in FamilyPose.entries.filter { !it.driving }) FamilyCharacter(pose, animate = false, modifier = Modifier.width(280.dp))
        }
    }
}

@GgfThemePreviews
@Composable
private fun ForestScenePreview() = GgfTheme {
    Column(Modifier.background(GgfTheme.colors.paper).padding(16.dp).width(960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ForestScene(planted = emptyList(), sprouts = 3, animals = emptySet(), animate = false, modifier = Modifier.fillMaxWidth())
        ForestScene(planted = List(5) { TreeSpecies.T01 }, sprouts = 7, animals = setOf(Animal.Squirrel), animate = false, modifier = Modifier.fillMaxWidth())
        ForestScene(planted = List(60) { TreeSpecies.entries[it % 10] }, sprouts = 4, animals = Animal.entries.toSet(), animate = false, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "숲 · 좁은 세로 400x720", widthDp = 400, heightDp = 720, showBackground = true)
@Composable
private fun ForestSceneNarrowPreview() = GgfTheme {
    ForestScene(planted = List(40) { TreeSpecies.T01 }, sprouts = 6, animals = Animal.entries.toSet(), animate = false, modifier = Modifier.fillMaxSize())
}

@GgfThemePreviews
@Composable
private fun LabelsPreview() = GgfTheme {
    Column(Modifier.background(GgfTheme.colors.paper).padding(24.dp).width(640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigNumberLabel(40)
            DemoBadge()
        }
        KidSentence("끝말잇기 4판 하면 도착해요", driving = true, icon = KidSentenceIcon.WordChain)
        KidSentence("노래 세 곡 들으면 도착해요", driving = true, icon = KidSentenceIcon.Song)
        Box(Modifier.width(300.dp)) { KidSentence("끝말잇기 5판 하면 도착해요", driving = true) }
    }
}

/** 모든 그림을 한 화면에 늘어놓는 확인용 갤러리(개발용). */
@Composable
internal fun CharacterGallery() {
    Column(Modifier.background(GgfTheme.colors.sceneSky).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GgfArt.entries.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                row.forEach { art ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtImage(art, contentDescription = art.label, modifier = Modifier.size(width = 140.dp, height = 120.dp))
                        Text(art.label, style = GgfTheme.type.label, color = GgfTheme.colors.ink)
                    }
                }
            }
        }
    }
}

@GgfThemePreviews
@Composable
private fun CharacterGalleryPreview() = GgfTheme { CharacterGallery() }
