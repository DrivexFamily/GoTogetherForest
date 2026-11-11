package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMathTest {

    @Test
    fun `진행률 25 50 75에서 포즈가 바뀐다`() {
        assertEquals(FamilyPose.DriveStart, drivePoseFor(0))
        assertEquals(FamilyPose.DriveStart, drivePoseFor(24))
        assertEquals(FamilyPose.DriveLookOut, drivePoseFor(25))
        assertEquals(FamilyPose.DriveLookOut, drivePoseFor(49))
        assertEquals(FamilyPose.DriveWave, drivePoseFor(50))
        assertEquals(FamilyPose.DriveWave, drivePoseFor(74))
        assertEquals(FamilyPose.DriveAlmost, drivePoseFor(75))
        assertEquals(FamilyPose.DriveAlmost, drivePoseFor(100))
        assertEquals(FamilyPose.DriveStart, drivePoseFor(-5))
        assertEquals(FamilyPose.DriveAlmost, drivePoseFor(140))
    }

    @Test
    fun `길 그림 설명은 진행률 뒤에 지금 포즈 문장을 붙인다`() {
        assertEquals("목적지까지 0퍼센트 왔어요. 출발! 아이가 두 팔을 들고 있어요", progressRoadDescription(0))
        assertEquals("목적지까지 40퍼센트 왔어요. 아이가 닫힌 창으로 바깥을 보고 있어요", progressRoadDescription(40))
        assertEquals("목적지까지 50퍼센트 왔어요. 아이가 손을 흔들고 있어요", progressRoadDescription(50))
        assertEquals("목적지까지 100퍼센트 왔어요. 거의 다 왔어요! 아이가 손뼉을 쳐요", progressRoadDescription(100))
        // 범위 밖 값은 그림과 같게 0..100으로 자른다
        assertEquals(progressRoadDescription(100), progressRoadDescription(130))
        for (p in 0..100) assertTrue(progressRoadDescription(p).endsWith(drivePoseFor(p).description))
    }

    @Test
    fun `주행 포즈 4개는 서로 다른 차 그림이다`() {
        val arts = FamilyPose.entries.filter { it.driving }.map { carArtFor(it) }
        arts.forEach { assertNotNull(it) }
        assertEquals(4, arts.toSet().size)
        FamilyPose.entries.filter { !it.driving }.forEach { assertNull(carArtFor(it)) }
    }

    @Test
    fun `정지 장면은 박자 0과 같다`() {
        for (pose in FamilyPose.entries) {
            for (m in FamilyMember.entries) {
                assertEquals(poseTransforms(pose, m, 0f), stillTransforms(pose, m))
            }
            for ((_, t) in carTransforms(pose, 0f)) {
                assertEquals(0f, t.rotation, 0.001f)
                assertEquals(0f, t.translateY, 0.001f)
            }
        }
    }

    @Test
    fun `주차 포즈의 정지 장면은 실루엣이 서로 다르다`() {
        val still = listOf(FamilyPose.ParkWave, FamilyPose.ParkCheer, FamilyPose.ParkRun).map { stillTransforms(it, FamilyMember.Child) }
        assertEquals(3, still.toSet().size)
        // 손 흔들기·축하는 팔을 든다(어깨 기준 90도 넘게 회전)
        assertTrue(kotlin.math.abs(stillTransforms(FamilyPose.ParkWave, FamilyMember.Child).getValue("armR").rotation) > 90f)
        assertTrue(kotlin.math.abs(stillTransforms(FamilyPose.ParkCheer, FamilyMember.Child).getValue("armL").rotation) > 90f)
    }

    @Test
    fun `움직임은 정해진 횟수만 하고 멈춘다`() {
        for (pose in FamilyPose.entries) {
            val spec = motionSpecFor(pose)
            assertTrue(spec.cycles in 1..6)
            assertTrue("$pose 총 ${spec.totalMs}ms", spec.totalMs <= 3_000L)
        }
    }

    @Test
    fun `움직이는 동안에는 관절이 실제로 움직인다`() {
        assertNotEquals(poseTransforms(FamilyPose.ParkWave, FamilyMember.GuardianA, 0f), poseTransforms(FamilyPose.ParkWave, FamilyMember.GuardianA, 0.25f))
        assertNotEquals(carTransforms(FamilyPose.DriveWave, 0f), carTransforms(FamilyPose.DriveWave, 0.25f))
    }

    @Test
    fun `가족은 발끝이 같은 선에 선다`() {
        val members = FamilyMember.entries
        val origins = StandingLayout.origins(members)
        // GPT 그림(결정 #29): 그림 아래 끝이 발끝이라 위쪽 + 키 = 바닥선
        members.zip(origins).forEach { (m, o) ->
            assertEquals(StandingLayout.GROUND, o.second + m.heightRatio, 0.0001f)
        }
        val expected = members.sumOf { (it.heightRatio * it.picture.aspect).toDouble() }.toFloat() - 2 * StandingLayout.OVERLAP
        assertEquals(expected, StandingLayout.width(members), 0.0001f)
        // 아이가 어른보다 작다
        assertTrue(FamilyMember.Child.heightRatio < FamilyMember.GuardianA.heightRatio)
    }
}
