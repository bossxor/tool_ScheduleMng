package com.bossxor.harugyeol.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.R
import com.bossxor.harugyeol.habit.*
import java.time.LocalDate
import java.time.YearMonth

object HabitArt {
    val options = listOf(
        "book" to "독서", "pencil" to "공부", "language" to "외국어", "code" to "코딩", "music" to "음악", "draw" to "그림", "camera" to "사진", "idea" to "아이디어",
        "workout" to "운동", "run" to "달리기", "walk" to "걷기", "bike" to "자전거", "swim" to "수영", "yoga" to "요가", "steps" to "계단", "ball" to "공놀이",
        "water" to "물", "pill" to "약", "fruit" to "과일", "vegetable" to "채소", "no_sugar" to "당 줄이기", "no_alcohol" to "금주", "no_smoke" to "금연", "tooth" to "양치", "shower" to "씻기", "skin" to "피부",
        "sleep" to "수면", "wake" to "기상", "coffee" to "커피", "cook" to "요리", "clean" to "청소", "laundry" to "세탁", "plant" to "식물", "pet" to "반려동물", "phone_off" to "휴대폰 쉬기", "money" to "돈", "save" to "저축",
        "meditate" to "명상", "journal" to "일기", "gratitude" to "감사", "family" to "가족", "smile" to "미소", "check" to "체크", "star" to "별", "flag" to "목표",
    )
    val drawables = mapOf(
        "ball" to R.drawable.habit_ball,
        "bike" to R.drawable.habit_bike,
        "book" to R.drawable.habit_book,
        "camera" to R.drawable.habit_camera,
        "check" to R.drawable.habit_check,
        "clean" to R.drawable.habit_clean,
        "code" to R.drawable.habit_code,
        "coffee" to R.drawable.habit_coffee,
        "cook" to R.drawable.habit_cook,
        "draw" to R.drawable.habit_draw,
        "family" to R.drawable.habit_family,
        "flag" to R.drawable.habit_flag,
        "fruit" to R.drawable.habit_fruit,
        "gratitude" to R.drawable.habit_gratitude,
        "idea" to R.drawable.habit_idea,
        "journal" to R.drawable.habit_journal,
        "language" to R.drawable.habit_language,
        "laundry" to R.drawable.habit_laundry,
        "meditate" to R.drawable.habit_meditate,
        "money" to R.drawable.habit_money,
        "ms_100" to R.drawable.habit_ms_100,
        "ms_3" to R.drawable.habit_ms_3,
        "ms_30" to R.drawable.habit_ms_30,
        "ms_7" to R.drawable.habit_ms_7,
        "music" to R.drawable.habit_music,
        "no_alcohol" to R.drawable.habit_no_alcohol,
        "no_smoke" to R.drawable.habit_no_smoke,
        "no_sugar" to R.drawable.habit_no_sugar,
        "pencil" to R.drawable.habit_pencil,
        "pet" to R.drawable.habit_pet,
        "phone_off" to R.drawable.habit_phone_off,
        "pill" to R.drawable.habit_pill,
        "plant" to R.drawable.habit_plant,
        "run" to R.drawable.habit_run,
        "save" to R.drawable.habit_save,
        "shower" to R.drawable.habit_shower,
        "skin" to R.drawable.habit_skin,
        "sleep" to R.drawable.habit_sleep,
        "smile" to R.drawable.habit_smile,
        "star" to R.drawable.habit_star,
        "state_all_done" to R.drawable.habit_state_all_done,
        "state_empty_archive" to R.drawable.habit_state_empty_archive,
        "state_empty_habits" to R.drawable.habit_state_empty_habits,
        "state_empty_search" to R.drawable.habit_state_empty_search,
        "state_empty_tasks" to R.drawable.habit_state_empty_tasks,
        "state_empty_trash" to R.drawable.habit_state_empty_trash,
        "state_update" to R.drawable.habit_state_update,
        "steps" to R.drawable.habit_steps,
        "swim" to R.drawable.habit_swim,
        "tooth" to R.drawable.habit_tooth,
        "vegetable" to R.drawable.habit_vegetable,
        "wake" to R.drawable.habit_wake,
        "walk" to R.drawable.habit_walk,
        "water" to R.drawable.habit_water,
        "workout" to R.drawable.habit_workout,
        "yoga" to R.drawable.habit_yoga,
    )
}

@Composable fun HabitArtIcon(key: String?, modifier: Modifier = Modifier) {
    val id = HabitArt.drawables[key] ?: return
    Image(painterResource(id), contentDescription = null, modifier = modifier)
}

@Composable fun StickerCollectionScreen(data: AppData, today: LocalDate, onBack: () -> Unit) {
    val c = LocalHaru.current
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("스티커 모음", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PillButton("이전 달", c.surface, c.text) { month = month.minusMonths(1) }
                Text("${month.year}년 ${month.monthValue}월", style = HaruType.headline, color = c.text)
                PillButton("다음 달", c.surface, c.text) { month = month.plusMonths(1) }
            }
            val total = data.habits.sumOf { habit -> habit.logs.count { log -> YearMonth.from(log.date) == month && !log.date.isAfter(today) && habit.isAchieved(log.date) } }
            Text("이번 달 스티커 ${total}개", style = HaruType.headline, color = c.text)
            if (data.habits.isEmpty()) {
                HabitArtIcon("state_empty_habits", Modifier.size(96.dp))
                Text("첫 습관을 만들면 스티커를 모을 수 있어요", style = HaruType.body, color = c.textSub)
            }
            data.habits.sortedBy { it.sortOrder }.forEach { habit ->
                PopCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(habit.name + if (habit.archivedAt != null) " · 보관됨" else "", style = HaruType.headline, color = c.text)
                        val achieved = habit.logs.filter { YearMonth.from(it.date) == month && !it.date.isAfter(today) && habit.isAchieved(it.date) }.sortedBy { it.date }
                        if (achieved.isEmpty()) Text("아직 이 달의 스티커가 없어요", style = HaruType.bodyS, color = c.textSub)
                        achieved.chunked(7).forEach { week -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { week.forEach { log ->
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Sticker(habit.name, palette(habit.colorIndex), DayState.ACHIEVED, 1f, 34.dp, habit.iconKey)
                                Text("${log.date.dayOfMonth}일", style = HaruType.caption, color = c.textSub)
                            }
                        } } }
                        val streak = habit.streak(today)
                        Text("마일스톤 · 최장 ${streak.longest}${if (streak.unit == StreakUnit.WEEK) "주" else "일"}", style = HaruType.bodyS, color = c.textSub)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(3, 7, 30, 100).forEach { n -> Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                HabitArtIcon("ms_$n", Modifier.size(44.dp).then(if (streak.longest < n) Modifier.background(c.stripeBg) else Modifier))
                                Text("$n${if (streak.unit == StreakUnit.WEEK) "주" else "일"}${if (streak.longest >= n) " ✓" else ""}", style = HaruType.caption, color = c.textSub)
                            } }
                        }
                    }
                }
            }
        }
    }
}
