package com.xnote.app

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import com.xnote.app.design.XNoteButtonContentSpacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.XNoteButtonSize
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteDrawer
import com.xnote.app.design.XNoteDrawerPlacement
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteDarkPrimaryColor
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNotePageState
import com.xnote.app.design.XNotePopupPlacement
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbar
import com.xnote.app.design.XNoteRichTextToolbarState
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.XNoteTheme
import com.xnote.app.design.rememberXNoteScrollEdgeState
import com.xnote.app.design.rememberXNotePopupAnchor
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.design.liquidglass.LiquidBottomTab
import com.xnote.app.design.liquidglass.LiquidBottomTabs
import com.xnote.app.design.liquidglass.LiquidButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// -- Tests

class XNoteDesignSystemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buttonsShareForegroundAndIconSpacingAcrossThemesAndStates() {
        var dark by mutableStateOf(false)
        var colored by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var actual = Color.Unspecified
        var expected = Color.Unspecified
        var expectedSpacing = 0f
        composeRule.setContent {
            XNoteTheme(darkTheme = dark, reduceMotion = true) {
                val normal = MaterialTheme.colorScheme.onSurface
                val density = LocalDensity.current
                SideEffect {
                    expected = if (colored) Color.White else normal
                    expectedSpacing = with(density) { XNoteButtonContentSpacing.toPx() }
                }
                LiquidButton(onClick = {}, backdrop = rememberLayerBackdrop(), enabled = enabled,
                    tint = if (colored) MaterialTheme.colorScheme.primary else Color.Unspecified) {
                    val foreground = LocalContentColor.current
                    SideEffect { actual = foreground }
                    Box(Modifier.size(20.dp).testTag("button-icon"))
                    Text("完成", modifier = Modifier.testTag("button-label"))
                }
            }
        }
        for (isDark in listOf(false, true)) {
            for (isColored in listOf(false, true)) {
                for (isEnabled in listOf(false, true)) {
                    composeRule.runOnIdle { dark = isDark; colored = isColored; enabled = isEnabled }
                    composeRule.runOnIdle { assertEquals(expected, actual) }
                    val icon = composeRule.onNodeWithTag("button-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val label = composeRule.onNodeWithTag("button-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(expectedSpacing, label.left - icon.right, 1f)
                }
            }
        }
    }

    @Test
    fun liquidButtonUsesCompactControlSize() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                LiquidButton(
                    onClick = {},
                    backdrop = backdrop,
                    modifier = Modifier.testTag("compact-button"),
                ) {
                    Text("完成")
                }
            }
        }

        composeRule.onNodeWithTag("compact-button")
            .assertHeightIsEqualTo(XNoteButtonSize)
    }

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
                                    iconRes = R.drawable.ic_keyline_stroke_search,
                                    contentDescription = "操作一",
                                    onClick = { clickedAction = "一" },
                                ),
                                XNoteHeaderAction(
                                    iconRes = R.drawable.ic_keyline_stroke_plus,
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
    fun drawerScrimDismissesOnClickAndIgnoresContentClicks() {
        var visible by mutableStateOf(true)

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                XNotePageScaffold(
                    backdrop = backdrop,
                    content = {},
                    overlay = {
                        XNoteDrawer(
                            visible = visible,
                            onDismissRequest = { visible = false },
                            title = "选择笔记本",
                            backdrop = backdrop,
                            placement = XNoteDrawerPlacement.Bottom,
                        ) {
                            Text("全部笔记")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("选择笔记本").assertIsDisplayed()
        composeRule.onNodeWithText("全部笔记").performTouchInput { click() }
        composeRule.onNodeWithText("选择笔记本").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-overlay-scrim").performTouchInput {
            click(percentOffset(0.5f, 0.05f))
        }
        assertTrue(
            composeRule.onAllNodesWithText("选择笔记本").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun dropdownCanReverseDuringAnimationAndDismissPromptly() {
        var expanded by mutableStateOf(false)
        composeRule.setContent {
            XNoteTheme(reduceMotion = false) {
                val backdrop = rememberLayerBackdrop()
                val anchor = rememberXNotePopupAnchor()
                XNotePageScaffold(backdrop = backdrop, content = {}, overlay = {
                    Box(Modifier.align(Alignment.Center).size(40.dp).xNotePopupAnchor(anchor))
                    XNoteDropdownMenu(
                        expanded = expanded, onDismissRequest = { expanded = false },
                        items = listOf(XNoteDropdownMenuItem("菜单动画验收", onClick = {})),
                        backdrop = backdrop, anchor = anchor,
                        modifier = Modifier.testTag("animated-menu"),
                    )
                })
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { expanded = true }
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onNodeWithText("菜单动画验收").assertIsDisplayed()
        composeRule.runOnUiThread { expanded = false }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.runOnUiThread { expanded = true }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("菜单动画验收").assertIsDisplayed()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(context.getExternalFilesDir(null), "dropdown-fixes.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.runOnUiThread { expanded = false }
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.onNodeWithTag("animated-menu").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun dropdownWrapsContentAndUsesItsAnchorPosition() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                val menuAnchor = rememberXNotePopupAnchor()
                XNotePageScaffold(
                    backdrop = backdrop,
                    content = {},
                    overlay = {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .xNotePopupAnchor(menuAnchor)
                                .testTag("dropdown-anchor"),
                        )
                        XNoteDropdownMenu(
                            expanded = true,
                            onDismissRequest = {},
                            items = listOf(
                                XNoteDropdownMenuItem(
                                    label = "短项",
                                    onClick = {},
                                ),
                            ),
                            backdrop = backdrop,
                            anchor = menuAnchor,
                            placement = XNotePopupPlacement.BelowStart,
                            modifier = Modifier.testTag("dropdown-panel"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val anchorBounds = composeRule.onNodeWithTag("dropdown-anchor")
            .fetchSemanticsNode().boundsInRoot
        val popupBounds = composeRule.onNodeWithTag("dropdown-panel")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(popupBounds.width < rootBounds.width / 2f)
        assertTrue(kotlin.math.abs(popupBounds.left - anchorBounds.left) < 2f)
        assertTrue(popupBounds.top > anchorBounds.bottom)
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

    @Test
    fun darkThemeUsesTheDocumentedPrimaryColor() {
        var actualPrimary = Color.Unspecified

        composeRule.setContent {
            XNoteTheme(darkTheme = true, reduceMotion = true) {
                val primary = MaterialTheme.colorScheme.primary
                SideEffect { actualPrimary = primary }
                Text("深色主题")
            }
        }

        composeRule.onNodeWithText("深色主题").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(XNoteDarkPrimaryColor, actualPrimary)
        }
    }

    @Test
    fun pageScaffoldTracksBothScrollEdges() {
        var canScrollBackward = false
        var canScrollForward = false

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                val scrollState = rememberScrollState()
                val scrollEdgeState = rememberXNoteScrollEdgeState(scrollState)
                SideEffect {
                    canScrollBackward = scrollEdgeState.canScrollBackward
                    canScrollForward = scrollEdgeState.canScrollForward
                }
                XNotePageScaffold(
                    backdrop = backdrop,
                    scrollEdgeState = scrollEdgeState,
                    scrollEdges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("scroll-edge-content")
                                .verticalScroll(scrollState),
                        ) {
                            repeat(80) { index -> Text("滚动内容 $index") }
                        }
                    },
                )
            }
        }

        composeRule.waitUntil(5_000) { canScrollForward }
        composeRule.runOnIdle {
            assertFalse(canScrollBackward)
            assertTrue(canScrollForward)
        }
        composeRule.onNodeWithTag("scroll-edge-content").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { canScrollBackward }
    }

    @Test
    fun bottomTabsHandleClickSelection() {
        var selectedIndex = 0

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val backdrop = rememberLayerBackdrop()
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = { selectedIndex = it },
                    backdrop = backdrop,
                    tabsCount = 2,
                ) {
                    LiquidBottomTab(onClick = { selectedIndex = 0 }) { Text("笔记") }
                    LiquidBottomTab(onClick = { selectedIndex = 1 }) { Text("智能") }
                }
            }
        }

        composeRule.onNodeWithText("智能").performClick()
        composeRule.runOnIdle { assertEquals(1, selectedIndex) }
    }

    @Test
    fun bottomTabsHandleTouchSelection() {
        var selectedIndex = 0

        composeRule.setContent {
            XNoteTheme(reduceMotion = false) {
                val backdrop = rememberLayerBackdrop()
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = { selectedIndex = it },
                    backdrop = backdrop,
                    tabsCount = 3,
                    modifier = Modifier.testTag("touch-liquid-tabs"),
                ) {
                    LiquidBottomTab(onClick = { selectedIndex = 0 }) { Text("笔记") }
                    LiquidBottomTab(onClick = { selectedIndex = 1 }) { Text("智能") }
                    LiquidBottomTab(onClick = { selectedIndex = 2 }) { Text("我的") }
                }
            }
        }

        composeRule.onNodeWithText("智能").performTouchInput { click() }
        composeRule.waitUntil(5_000) { selectedIndex == 1 }
    }

    @Test
    fun bottomTabsDragToTheNearestDestination() {
        var selectedIndex = 0

        composeRule.setContent {
            XNoteTheme(reduceMotion = false) {
                val backdrop = rememberLayerBackdrop()
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = { selectedIndex = it },
                    backdrop = backdrop,
                    tabsCount = 3,
                    modifier = Modifier.testTag("draggable-liquid-tabs"),
                ) {
                    LiquidBottomTab(onClick = { selectedIndex = 0 }) { Text("笔记") }
                    LiquidBottomTab(onClick = { selectedIndex = 1 }) { Text("智能") }
                    LiquidBottomTab(onClick = { selectedIndex = 2 }) { Text("我的") }
                }
            }
        }

        composeRule.onNodeWithTag("draggable-liquid-tabs").performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { selectedIndex == 2 }
    }
}
