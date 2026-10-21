package com.example.danmuapiapp.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppToastTest {
    @Test
    fun `short confirmations use the Flutter compatible duration`() {
        assertEquals(
            1_600L,
            defaultAppToastDurationMillis(AppToastKind.Success, "已保存")
        )
        assertEquals(
            1_600L,
            defaultAppToastDurationMillis(AppToastKind.Info, "正在刷新")
        )
    }

    @Test
    fun `warnings and errors stay readable without becoming Snackbars`() {
        assertEquals(
            2_200L,
            defaultAppToastDurationMillis(AppToastKind.Warning, "请输入视频地址")
        )
        assertEquals(
            2_600L,
            defaultAppToastDurationMillis(AppToastKind.Warning, "请".repeat(30))
        )
        assertEquals(
            3_600L,
            defaultAppToastDurationMillis(AppToastKind.Error, "错".repeat(43))
        )
    }

    @Test
    fun `kind inference gives errors precedence over warning words`() {
        assertEquals(AppToastKind.Error, inferAppToastKind("保存失败，请稍后重试"))
        assertEquals(AppToastKind.Warning, inferAppToastKind("请输入搜索关键词"))
        assertEquals(AppToastKind.Success, inferAppToastKind("版本信息已复制"))
        assertEquals(AppToastKind.Info, inferAppToastKind("正在检查更新"))
    }

    @Test
    fun `controller replaces messages and keeps actionable feedback for five seconds`() {
        val controller = AppToastController()
        controller.show(
            text = "已删除 3 项",
            kind = AppToastKind.Success,
            actionLabel = "撤销",
            action = {}
        )
        val first = requireNotNull(controller.currentMessage)
        assertEquals("撤销", first.actionLabel)
        assertTrue(first.durationMillis >= 5_000L)

        controller.show("下一条提示", AppToastKind.Info)
        val second = requireNotNull(controller.currentMessage)
        assertTrue(second.id > first.id)
        assertEquals("下一条提示", second.text)

        controller.dismiss(first.id)
        assertEquals(second, controller.currentMessage)
        controller.dismiss(second.id)
        assertNull(controller.currentMessage)
    }

    @Test
    fun `blank messages are ignored`() {
        val controller = AppToastController()
        controller.show("   ")
        assertNull(controller.currentMessage)
    }
}
