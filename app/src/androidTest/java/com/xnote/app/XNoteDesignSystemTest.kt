package com.xnote.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNotePageState
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbar
import com.xnote.app.design.XNoteRichTextToolbarState
import com.xnote.app.design.XNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// -- Tests

class XNoteDesignSystemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerSupportsTwoActions() {
        var clickedAction = ""

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                XNotePageScaffold(
                    backdrop = backdrop,
                    content = {},
                    overlay = {
                        XNoteHeader(
                            title = "工具调用详情",
                            backdrop = backdrop,
                            onBack = {},
                            actions = listOf(
                                XNoteHeaderAction(
                                    iconRes = R.drawable.ic_lucide_search,
                                    contentDescription = "操作一",
                                    onClick = { clickedAction = "一" },
                                ),
                                XNoteHeaderAction(
                                    iconRes = R.drawable.ic_lucide_plus,
                                    contentDescription = "操作二",
                                    onClick = { clickedAction = "二" },
                                ),
                            ),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("工具调用详情").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("操作二").performClick()
        composeRule.runOnIdle { assertEquals("二", clickedAction) }
    }

    @Test
    fun pageScaffoldRendersLoadingAndErrorStates() {
        var pageState by mutableStateOf<XNotePageState>(
            XNotePageState.Loading("正在载入笔记"),
        )
        var retried = false

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                XNotePageScaffold(
                    backdrop = backdrop,
                    pageState = pageState,
                    onPageStateAction = { retried = true },
                    content = {},
                )
            }
        }

        composeRule.onNodeWithText("正在载入笔记").assertIsDisplayed()
        composeRule.runOnUiThread {
            pageState = XNotePageState.Error(
                title = "无法打开笔记",
                description = "请稍后重试",
                actionLabel = "重试",
            )
        }
        composeRule.onNodeWithText("无法打开笔记").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun richTextToolbarEmitsDocumentIntent() {
        var selectedAction: XNoteRichTextAction? = null

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                XNotePageScaffold(
                    backdrop = backdrop,
                    content = {
                        XNoteRichTextToolbar(
                            state = XNoteRichTextToolbarState(),
                            onAction = { selectedAction = it },
                            backdrop = backdrop,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("粗体").performClick()
        composeRule.runOnIdle {
            assertEquals(XNoteRichTextAction.Bold, selectedAction)
        }
    }

    @Test
    fun commonDialogAndDropdownDispatchActions() {
        var dialogConfirmed = false
        var dropdownSelected = false
        var dropdownExpanded by mutableStateOf(true)

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                XNotePageScaffold(
                    backdrop = backdrop,
                    content = {},
                    overlay = {
                        XNoteDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            items = listOf(
                                XNoteDropdownMenuItem(
                                    label = "按更新时间排序",
                                    onClick = { dropdownSelected = true },
                                ),
                            ),
                            backdrop = backdrop,
                        )
                        XNoteDialog(
                            visible = !dropdownExpanded,
                            onDismissRequest = {},
                            title = "删除笔记？",
                            backdrop = backdrop,
                            confirmAction = XNoteDialogAction(
                                label = "删除",
                                onClick = { dialogConfirmed = true },
                                destructive = true,
                            ),
                        ) {
                            Text("笔记将移入回收站。")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("按更新时间排序").performClick()
        composeRule.onNodeWithText("删除笔记？").assertIsDisplayed()
        composeRule.onNodeWithText("笔记将移入回收站。").performTouchInput { click() }
        composeRule.onNodeWithText("删除笔记？").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle {
            assertTrue(dropdownSelected)
            assertTrue(dialogConfirmed)
        }
    }

    @Test
    fun themeCanForceReducedMotionForAccessibilityTesting() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                Text(
                    if (LocalXNoteInteractionSettings.current.reduceMotion) {
                        "减少动画已启用"
                    } else {
                        "减少动画未启用"
                    },
                )
            }
        }

        composeRule.onNodeWithText("减少动画已启用").assertIsDisplayed()
    }
}
