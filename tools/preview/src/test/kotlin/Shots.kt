import android.graphics.Bitmap
import android.graphics.Canvas
import com.terpenesalad.nugofwar.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Shots {
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").also { it.mkdirs() }

    private fun save(b: Bitmap, name: String) {
        FileOutputStream(File(dir, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("saved $name")
    }

    @Test fun nugSheet() {
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF000000.toInt())
        for (shape in 0..3) for (ci in 0..3) {
            val look = Look.of(Game.COLORS[ci * 2 % 8], shape, 11 + ci)
            Gfx.nug(c, 240f + ci * 460f, 150f + shape * 255f, 82f, look, 0.4f, 0.2f, 0f, false, 0f, 0)
        }
        save(bmp, "01_nugs_good.png")
        c.drawColor(0xFF000000.toInt())
        for (shape in 0..3) for (k in 0..3) {
            val look = Look.of(Game.BAD_COLORS[(k + shape) % 6], shape, k)
            Gfx.nug(c, 240f + k * 460f, 150f + shape * 255f, 82f, look, -0.4f, 0f, 0f, false, 0f, 1, 1f, hat = k == 3)
        }
        save(bmp, "02_nugs_bad.png")
        c.drawColor(0xFF000000.toInt())
        for (st in 0..3) {
            Gfx.label(c, Look.STYLE_NAMES[st].uppercase(), 40f, 175f + st * 255f, 40f, 0xFFAAAAAA.toInt(), android.graphics.Paint.Align.LEFT, shadow = false)
            for (k in 0..3) {
                val col = if (k == 3) Game.BAD_COLORS[0] else Game.COLORS[k]
                val look = Look.of(col, k, 11 + k, st)
                Gfx.nug(c, 520f + k * 400f, 150f + st * 255f, 82f, look, 0.4f, 0.2f, 0f, false, 0f, if (k == 3) 1 else 0)
            }
        }
        save(bmp, "00_styles.png")
    }

    private fun clear(c: Ctl) {
        c.jumpP = false; c.attackP = false; c.blockP = false; c.throwP = false; c.confirmP = false
        c.backP = false; c.startP = false; c.upP = false; c.downP = false; c.leftP = false; c.rightP = false
    }

    @Test fun gameplay() {
        val app = RuntimeEnvironment.getApplication()
        val input = InputHub(app)
        val prefs = app.getSharedPreferences("shots", 0)
        prefs.edit().clear().putBoolean("projector", false).commit()
        val game = Game(input, Sound(app), prefs) {}
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var frameNo = 0
        fun frames(n: Int, setup: (Array<Ctl>, Int) -> Unit = { _, _ -> }) {
            repeat(n) {
                input.ctl.forEach { clear(it) }
                setup(input.ctl, frameNo)
                game.update(1f / 60f)
                frameNo++
            }
        }
        fun shot(name: String) { canvas.drawColor(0); game.draw(canvas); save(bmp, name) }

        frames(30); shot("03_title.png")
        frames(1) { c, _ -> c[0].confirmP = true }          // Versus Brawl
        frames(5); shot("04_arena.png")
        frames(1) { c, _ -> c[0].confirmP = true }          // Bookshelf
        frames(1) { c, _ -> c[1].confirmP = true }          // P2 joins
        frames(1) { c, _ -> c[2].confirmP = true }          // P3 joins
        frames(1) { c, _ -> c[1].rightP = true }
        frames(10); shot("05_lobby.png")
        frames(1) { c, _ -> c[0].confirmP = true; c[1].confirmP = true; c[2].confirmP = true }
        frames(90)                                          // countdown -> play
        frames(60); shot("06_round_intro.png")
        // scripted brawl
        val script: (Array<Ctl>, Int) -> Unit = { c, f ->
            c[0].mx = if ((f / 50) % 3 == 0) -1f else 1f
            c[0].attack = f % 20 < 2; c[0].attackP = f % 20 == 0
            c[0].jump = f % 70 < 10; c[0].jumpP = f % 70 == 0
            c[0].throwP = f % 300 == 150
            c[1].mx = if ((f / 40) % 2 == 0) -1f else 1f
            c[1].jumpP = f % 45 == 0; c[1].jump = f % 45 < 12
            c[1].attack = f % 25 < 2; c[1].attackP = f % 25 == 3
            c[1].block = f % 120 in 60..80; c[1].blockP = f % 120 == 60
            c[2].mx = if ((f / 65) % 2 == 0) 1f else -1f
            c[2].attack = f % 15 < 2; c[2].attackP = f % 15 == 1
            c[2].jumpP = f % 55 == 5; c[2].jump = f % 55 in 5..15
            c[2].my = if (f % 200 < 20) -1f else 0f
        }
        for (k in 0 until 8) { frames(150, script); shot("07_brawl_${k}.png") }

        // survival
        frames(1) { c, _ -> c[0].startP = true }
        frames(1) { c, _ -> c[0].downP = true }
        frames(1) { c, _ -> c[0].downP = true }
        frames(1) { c, _ -> c[0].confirmP = true }          // quit to menu
        frames(1) { c, _ -> c[0].downP = true }
        frames(1) { c, _ -> c[0].confirmP = true }          // survival
        frames(1) { c, _ -> c[0].downP = true }             // Gallery wall
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(1) { c, _ -> c[0].confirmP = true; c[1].confirmP = true }
        frames(5) ; frames(1) { c, _ -> c[1].confirmP = true }
        frames(120)
        for (k in 0 until 6) { frames(180, script); shot("08_survival_${k}.png") }

        // wall editor
        frames(1) { c, _ -> c[0].startP = true }
        frames(1) { c, _ -> c[0].downP = true }; frames(1) { c, _ -> c[0].downP = true }
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(1) { c, _ -> c[0].downP = true }; frames(1) { c, _ -> c[0].downP = true }
        frames(1) { c, _ -> c[0].confirmP = true }          // wall setup
        frames(1) { c, _ -> c[0].confirmP = true }          // edit wall 1
        frames(40) { c, _ -> c[0].mx = -1f; c[0].my = -0.3f }
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(30) { c, _ -> c[0].mx = 1f }
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(20) { c, _ -> c[0].my = 1f }
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(25) { c, _ -> c[0].mx = 1f; c[0].my = 0.6f }
        shot("09_editor.png")
        frames(1) { c, _ -> c[0].backP = true }             // cancel the open corner
        frames(45) { c, _ -> c[0].mx = -1f; c[0].my = -1f }
        frames(1) { c, _ -> c[0].confirmP = true }          // pick up (if over a box)
        frames(20) { c, _ -> c[0].mx = 0.6f; c[0].my = 0.5f; c[0].ax = 1f }
        shot("09_editor_move.png")
    }
}
