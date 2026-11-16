package com.rio.board

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The sound effects. Everything is simple signal maths on the recorded samples, nothing is uploaded. */
object VoiceFx {
    val names = listOf("Normal", "Chipmunk", "Deep", "Robot", "Alien", "Echo", "Cave", "Telephone", "Monster", "Reverse")
    val icons = listOf("\uD83C\uDFA4", "\uD83D\uDC3F", "\uD83D\uDC3B", "\uD83E\uDD16", "\uD83D\uDC7D", "\uD83C\uDFD4", "\uD83D\uDD73", "\u260E", "\uD83D\uDC79", "\u23EA")

    fun apply(x: ShortArray, idx: Int, rate: Int): ShortArray {
        var f = FloatArray(x.size) { x[it] / 32768f }
        f = when (idx) {
            1 -> resample(f, 1.45f)
            2 -> resample(f, 0.72f)
            3 -> ring(f, 55f, rate, 0.9f)
            4 -> ring(resample(f, 1.2f), 380f, rate, 0.8f)
            5 -> echo(f, rate, 0.26f, 0.5f, 3)
            6 -> echo(f, rate, 0.11f, 0.62f, 6)
            7 -> telephone(f, rate)
            8 -> echo(ring(resample(f, 0.62f), 28f, rate, 0.5f), rate, 0.09f, 0.35f, 3)
            9 -> f.reversedArray()
            else -> f
        }
        var peak = 0f
        for (v in f) peak = max(peak, abs(v))
        val g = if (peak > 0.0001f) min(0.9f / peak, 4f) else 1f
        return ShortArray(f.size) { (f[it] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    /** Plays the samples faster (speed > 1: higher and quicker) or slower (speed < 1: deeper and slower). */
    private fun resample(f: FloatArray, speed: Float): FloatArray {
        val n = (f.size / speed).toInt()
        if (n <= 1) return f
        return FloatArray(n) { i ->
            val pos = i * speed
            val i0 = pos.toInt().coerceAtMost(f.size - 1)
            val i1 = min(i0 + 1, f.size - 1)
            val fr = pos - i0
            f[i0] * (1f - fr) + f[i1] * fr
        }
    }

    private fun ring(f: FloatArray, hz: Float, rate: Int, mix: Float): FloatArray =
        FloatArray(f.size) { i ->
            val m = sin(2.0 * PI * hz * i / rate).toFloat()
            f[i] * (1f - mix) + f[i] * m * mix
        }

    private fun echo(f: FloatArray, rate: Int, delay: Float, decay: Float, taps: Int): FloatArray {
        val d = (delay * rate).toInt().coerceAtLeast(1)
        val out = FloatArray(f.size + d * taps)
        for (i in f.indices) {
            out[i] += f[i]
            for (t in 1..taps) out[i + d * t] += f[i] * decay.pow(t)
        }
        return out
    }

    private fun telephone(f: FloatArray, rate: Int): FloatArray {
        val dt = 1f / rate
        val rcHigh = (1.0 / (2.0 * PI * 300.0)).toFloat()
        val aHigh = rcHigh / (rcHigh + dt)
        val rcLow = (1.0 / (2.0 * PI * 3400.0)).toFloat()
        val bLow = dt / (rcLow + dt)
        val out = FloatArray(f.size)
        var hy = 0f
        var hx = 0f
        var ly = 0f
        for (i in f.indices) {
            hy = aHigh * (hy + f[i] - hx)
            hx = f[i]
            ly += bLow * (hy - ly)
            out[i] = (ly * 1.6f).coerceIn(-1f, 1f)
        }
        return out
    }
}

/**
 * Voice changer: record, pick an effect, listen, then send it. The result is an audio file (m4a) that you can
 * share to WhatsApp or any chat app.
 */
class VoiceChangerActivity : Activity() {

    companion object {
        const val RATE = 44100
        const val MAX_SEC = 60
    }

    private val BG = 0xFF14141A.toInt()
    private val CHIP = 0xFF2A2A35.toInt()
    private val ACCENT = 0xFF7C4DFF.toInt()
    private val RED = 0xFFE5484D.toInt()
    private val ui = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }

    private var raw = ShortArray(0)
    private var processed = ShortArray(0)
    private var fxIdx = 0
    private var recording = false
    @Volatile private var recCount = 0
    private var track: AudioTrack? = null

    private lateinit var status: TextView
    private lateinit var recBtn: TextView
    private val chips = ArrayList<TextView>()

    private fun dp(v: Int) = (v * d).toInt()

    private fun box(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isVerticalScrollBarEnabled = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(28))
        }
        scroll.addView(col)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("\u2190", 24f, true).apply { setPadding(dp(4), dp(4), dp(14), dp(4)); setOnClickListener { finish() } })
            addView(label("Voice changer", 20f, true))
        })

        recBtn = label("\u25CF  Tap to record", 18f, true).apply {
            gravity = Gravity.CENTER
            background = box(ACCENT, 16)
            setPadding(dp(16), dp(22), dp(16), dp(22))
            layoutParams = lp(top = 18)
            setOnClickListener { onRecTap() }
        }
        col.addView(recBtn)
        status = label("Record up to $MAX_SEC seconds, then pick an effect.", 13f, false).apply {
            setTextColor(0xFF9AA3C0.toInt())
            gravity = Gravity.CENTER
            layoutParams = lp(top = 8)
        }
        col.addView(status)

        col.addView(label("Effect", 13f, true).apply { setTextColor(0xFF9AA3C0.toInt()); setPadding(dp(4), dp(18), 0, dp(2)) })
        VoiceFx.names.chunked(3).forEachIndexed { r, part ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(top = 6) }
            part.forEachIndexed { c, name ->
                val i = r * 3 + c
                val chip = label(VoiceFx.icons[i] + "\n" + name, 13f, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                    setOnClickListener { pickFx(i) }
                }
                chips.add(chip)
                row.addView(chip)
            }
            repeat(3 - part.size) { row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }) }
            col.addView(row)
        }
        paintChips()

        col.addView(button("\u25B6  Play", false) { play() })
        col.addView(button("Send in WhatsApp", true) { send(true) })
        col.addView(button("Share to other apps", false) { send(false) })
        col.addView(label(
            "Your voice stays on this phone. The effect is made here and the finished sound is an audio file you send like any other file.",
            12f, false
        ).apply { setTextColor(0xFF9AA3C0.toInt()); setPadding(dp(4), dp(14), dp(4), 0) })
        setContentView(scroll)
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun label(t: String, sp: Float, bold: Boolean) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(t: String, primary: Boolean, onClick: () -> Unit) = label(t, 16f, true).apply {
        gravity = Gravity.CENTER
        background = box(if (primary) ACCENT else CHIP, 14)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = lp(top = 12)
        setOnClickListener { onClick() }
    }

    private fun paintChips() {
        chips.forEachIndexed { i, c -> c.background = box(if (i == fxIdx) ACCENT else CHIP, 12) }
    }

    // ---------------------------------------------------------------- recording
    private fun onRecTap() {
        if (recording) {
            recording = false          // the recording thread notices and finishes
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        startRecording()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 41) {
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) startRecording()
            else toast("The microphone is needed to record your voice")
        }
    }

    private fun startRecording() {
        stopPlayback()
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192)
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            toast("Could not start the microphone")
            return
        }
        recording = true
        recCount = 0
        recBtn.text = "\u25A0  Stop"
        recBtn.background = box(RED, 16)
        val cap = RATE * MAX_SEC
        val acc = ShortArray(cap)
        val tick = object : Runnable {
            override fun run() {
                if (recording) {
                    status.text = "Recording...  " + (recCount / RATE) + " s"
                    ui.postDelayed(this, 200)
                }
            }
        }
        ui.post(tick)
        Thread {
            val buf = ShortArray(2048)
            try {
                rec.startRecording()
                while (recording && recCount < cap) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        val take = min(n, cap - recCount)
                        System.arraycopy(buf, 0, acc, recCount, take)
                        recCount += take
                    } else if (n < 0) break
                }
            } catch (e: Exception) {
            }
            try { rec.stop() } catch (e: Exception) { }
            rec.release()
            val got = acc.copyOf(recCount)
            runOnUiThread {
                recording = false
                recBtn.text = "\u25CF  Record again"
                recBtn.background = box(ACCENT, 16)
                raw = got
                if (raw.size < RATE / 4) {
                    status.text = "Too short, try again."
                } else {
                    status.text = "Recorded " + (raw.size / RATE) + " s. Pick an effect."
                    applyAndPlay()
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- effects + playback
    private fun pickFx(i: Int) {
        fxIdx = i
        paintChips()
        if (raw.isEmpty()) {
            toast("Record something first")
            return
        }
        applyAndPlay()
    }

    private fun applyAndPlay() {
        status.text = "Making the effect..."
        val src = raw
        val idx = fxIdx
        Thread {
            val out = VoiceFx.apply(src, idx, RATE)
            runOnUiThread {
                processed = out
                status.text = VoiceFx.names[idx] + " ready (" + (out.size / RATE) + " s)"
                play()
            }
        }.start()
    }

    private fun stopPlayback() {
        try {
            track?.stop()
        } catch (e: Exception) {
        }
        try {
            track?.release()
        } catch (e: Exception) {
        }
        track = null
    }

    private fun play() {
        if (processed.isEmpty()) {
            toast("Record something first")
            return
        }
        stopPlayback()
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(processed.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(processed, 0, processed.size)
            t.play()
            track = t
        } catch (e: Exception) {
            toast("Could not play the sound")
        }
    }

    // ---------------------------------------------------------------- send
    private fun send(whatsapp: Boolean) {
        if (processed.isEmpty()) {
            toast("Record something first")
            return
        }
        stopPlayback()
        status.text = "Preparing the file..."
        val pcm = processed
        Thread {
            var file: File? = null
            try {
                val dir = StickerStore.dir(this)
                dir.listFiles { f -> f.isFile && f.name.startsWith("pk_vc_") }
                    ?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
                file = File(dir, "pk_vc_" + System.currentTimeMillis() + ".m4a")
                encodeAac(pcm, file)
            } catch (e: Exception) {
                file = null
            }
            val f = file
            runOnUiThread {
                if (f == null || !f.exists() || f.length() == 0L) {
                    status.text = "Could not make the audio file."
                    return@runOnUiThread
                }
                status.text = "Done."
                val uri = RioFileProvider.uriFor(this, f)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    if (whatsapp) {
                        try {
                            startActivity(Intent(i).setPackage("com.whatsapp"))
                            return@runOnUiThread
                        } catch (e: Exception) {
                            toast("WhatsApp not found, pick another app")
                        }
                    }
                    startActivity(Intent.createChooser(i, "Send voice"))
                } catch (e: Exception) {
                    toast("Could not open the share screen")
                }
            }
        }.start()
    }

    /** PCM 16-bit mono -> AAC in an .m4a file. */
    private fun encodeAac(pcm: ShortArray, file: File) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxStarted = false
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        var outDone = false
        fun pts(samples: Int) = samples * 1_000_000L / RATE
        try {
            while (!outDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        ib.clear()
                        ib.order(ByteOrder.LITTLE_ENDIAN)
                        val n = min(ib.capacity() / 2, pcm.size - pos)
                        if (n <= 0) {
                            codec.queueInputBuffer(ii, 0, 0, pts(pos), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (k in 0 until n) ib.putShort(pcm[pos + k])
                            codec.queueInputBuffer(ii, 0, n * 2, pts(pos), 0)
                            pos += n
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxStarted = true
                } else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxStarted) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (e: Exception) { }
            codec.release()
            try { if (muxStarted) muxer.stop() } catch (e: Exception) { }
            muxer.release()
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        recording = false
        stopPlayback()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
