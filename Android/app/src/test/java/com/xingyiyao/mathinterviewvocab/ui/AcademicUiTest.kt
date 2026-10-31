package com.xingyiyao.mathinterviewvocab.ui

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AcademicUiTest {
    @get:Rule(order = 0)
    val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(context.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = context.packageName
                },
            )
        }
    }

    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun longChoiceAtTwoHundredPercentRemainsOperable() {
        var chosen = false
        val label = "Lebesgue dominated convergence theorem / 勒贝格控制收敛定理"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AcademicTheme {
                    Column(Modifier.width(360.dp)) {
                        ChoiceRow(label, false, { chosen = true },
                            supporting = "可出题 326 个词汇，排除已掌握的词汇")
                    }
                }
            }
        }
        compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction().performClick()
        assertTrue(chosen)
    }

    @Test
    fun readingColumnCanScrollLongContentWithoutHorizontalClipping() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AcademicTheme {
                    ReadingColumn(Modifier.width(360.dp)) {
                        SectionHeading("数学专业面试英文词汇", "手机分屏与大字体")
                        AcademicCard {
                            Text("noncentral chi-squared distribution, probability density function",
                                Modifier.fillMaxWidth())
                            Text("概率密度函数与非中心卡方分布。".repeat(20))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("数学专业面试英文词汇").assertIsDisplayed()
    }
}
