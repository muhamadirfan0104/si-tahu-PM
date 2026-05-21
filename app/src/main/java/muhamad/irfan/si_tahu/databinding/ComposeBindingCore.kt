package muhamad.irfan.si_tahu.databinding

import android.content.Context
import android.graphics.Bitmap
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import kotlin.jvm.JvmName

internal object SiTahuColors {
    val Screen = Color(0xFFFFFCF6)
    val ScreenWarm = Color(0xFFFFF9EE)
    val Card = Color(0xFFFFFFFC)
    val Glass = Color(0xFFFFFCF6)
    val CardSoft = Color(0xFFFFF3D6)

    val Primary = Color(0xFFC79A3D)
    val Primary2 = Color(0xFFE7C56C)
    val PrimaryDark = Color(0xFF8A6022)
    val PrimarySoft = Color(0xFFFFF3D6)
    val TextPrimary = Color(0xFF2A241B)
    val TextSecondary = Color(0xFF7E7464)
    val TextMuted = Color(0xFFA69B8A)
    val Line = Color(0xFFEBDDCA)
    val Danger = Color(0xFFC65B4A)
    val DangerSoft = Color(0xFFFFEDE7)
    val Green = Color(0xFF4D8D64)
    val GreenSoft = Color(0xFFEEF7EF)
    val Blue = Color(0xFFC79A3D)
    val BlueSoft = Color(0xFFFFF3D6)
    val Orange = Color(0xFFC79A3D)
    val OrangeSoft = Color(0xFFFFF3D6)
    val Purple = Color(0xFFC79A3D)
    val PurpleSoft = Color(0xFFFFF3D6)
}

private val SiTahuScheme = lightColorScheme(
    primary = SiTahuColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SiTahuColors.PrimarySoft,
    onPrimaryContainer = SiTahuColors.TextPrimary,
    secondary = SiTahuColors.Primary,
    background = SiTahuColors.Screen,
    surface = SiTahuColors.Card,
    onSurface = SiTahuColors.TextPrimary,
    outline = SiTahuColors.Line,
    error = SiTahuColors.Danger
)

@Composable
internal fun SiTahuBindingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SiTahuScheme, content = content)
}

internal fun bindingRoot(context: Context, content: @Composable () -> Unit): ComposeView {
    return ComposeView(context).apply {
        id = ViewCompat.generateViewId()
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Set content after the binding object is assigned. This prevents rare
        // lateinit crashes on fast devices where ComposeView composes immediately.
        post {
            setContent {
                SiTahuBindingTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = SiTahuColors.Screen
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

open class ComposeComponent {
    var isVisible by mutableStateOf(true)
    var visibility: Int
        get() = if (isVisible) View.VISIBLE else View.GONE
        set(value) { isVisible = value == View.VISIBLE }
    var isEnabled by mutableStateOf(true)
    var alpha by mutableFloatStateOf(1f)
    private var clickListener: View.OnClickListener? = null
    internal val hasClickListener: Boolean get() = clickListener != null

    fun setOnClickListener(listener: View.OnClickListener?) {
        clickListener = listener
    }

    fun performClick(anchor: View) {
        if (isEnabled) clickListener?.onClick(anchor)
    }

    fun post(action: () -> Unit) {
        action()
    }
}

class ComposeToolbarState : ComposeComponent() {
    var title by mutableStateOf("")
    var subtitle by mutableStateOf<String?>(null)
    var showBack by mutableStateOf(true)
    var onBack: (() -> Unit)? = null
}

open class ComposeTextState(initialText: String = "") : ComposeComponent() {
    @set:JvmName("setTextState")
    var text by mutableStateOf<CharSequence>(initialText)

    fun setText(value: CharSequence?) {
        val next = value ?: ""
        if (text.toString() == next.toString()) return
        text = next
    }
    fun setBackgroundResource(resId: Int) = Unit
}

class ComposeButtonState(initialText: String = "") : ComposeTextState(initialText)

class ComposeImageState : ComposeComponent() {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var tag: Any? = null
    fun setImageBitmap(value: Bitmap?) { bitmap = value }
}

class ComposeProgressState : ComposeComponent() {
    var progress by mutableIntStateOf(0)
}

class ComposeEditTextState(initialHint: String = "") : ComposeComponent() {
    private val editableFactory = Editable.Factory.getInstance()
    private val watchers = mutableListOf<TextWatcher>()
    var value by mutableStateOf("")
    var hint by mutableStateOf(initialHint)
    var error by mutableStateOf<CharSequence?>(null)
    var keyboardType by mutableStateOf(KeyboardType.Text)
    var singleLine by mutableStateOf(false)

    val text: Editable?
        get() = editableFactory.newEditable(value)

    fun setText(text: CharSequence?) {
        val newValue = text?.toString().orEmpty()
        if (newValue == value) return
        val before = value
        value = newValue
        notifyTextChanged(before, value)
    }

    fun setSelection(index: Int) = Unit
    fun requestFocus() = Unit

    fun addTextChangedListener(watcher: TextWatcher) {
        watchers += watcher
    }

    fun addTextChangedListener(afterTextChanged: (Editable?) -> Unit) {
        watchers += object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = afterTextChanged(s)
        }
    }

    fun doAfterTextChanged(afterTextChanged: (Editable?) -> Unit): TextWatcher {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = afterTextChanged(s)
        }
        watchers += watcher
        return watcher
    }

    internal fun updateFromUi(newValue: String) {
        if (newValue == value) return
        val before = value
        value = newValue
        notifyTextChanged(before, newValue)
    }

    private fun notifyTextChanged(before: String, after: String) {
        if (watchers.isEmpty()) return
        val editable = editableFactory.newEditable(after)
        val snapshot = watchers.toList()
        snapshot.forEach { it.beforeTextChanged(before, 0, before.length, after.length) }
        snapshot.forEach { it.onTextChanged(after, 0, before.length, after.length) }
        snapshot.forEach { it.afterTextChanged(editable) }
    }
}

class ComposeCheckState(initialText: String = "") : ComposeComponent() {
    var text by mutableStateOf(initialText)
    var isChecked by mutableStateOf(false)
    private var checkedListener: ((ComposeCheckState, Boolean) -> Unit)? = null

    fun setOnCheckedChangeListener(listener: ((ComposeCheckState, Boolean) -> Unit)?) {
        checkedListener = listener
    }

    internal fun updateChecked(value: Boolean) {
        if (isChecked == value) return
        isChecked = value
        checkedListener?.invoke(this, value)
    }
}

class ComposeSpinnerState : ComposeComponent() {
    var options by mutableStateOf<List<String>>(emptyList())
    var selectedItemPosition by mutableIntStateOf(0)
    var onItemSelectedListener: AdapterView.OnItemSelectedListener? = null

    var adapter: Any? = null
        set(value) {
            field = value
            options = when (value) {
                is List<*> -> value.map { it?.toString().orEmpty() }
                is Array<*> -> value.map { it?.toString().orEmpty() }
                else -> options
            }
            if (selectedItemPosition !in options.indices) selectedItemPosition = 0
        }

    val selectedItem: Any?
        get() = options.getOrNull(selectedItemPosition)

    fun setSelection(position: Int) {
        val newPosition = position.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        if (selectedItemPosition == newPosition) return
        selectedItemPosition = newPosition
        onItemSelectedListener?.onItemSelected(null, null, selectedItemPosition, selectedItemPosition.toLong())
    }

    internal fun updateSelection(position: Int) {
        setSelection(position)
    }
}

class ComposeRecyclerState : ComposeComponent() {
    var adapter by mutableStateOf<Any?>(null)
    var layoutManager: Any? = null
    var isNestedScrollingEnabled: Boolean = true
}

class ComposeWebViewState(context: Context) : ComposeComponent() {
    val webView = WebView(context)
    val settings get() = webView.settings
    var webViewClient: WebViewClient?
        get() = null
        set(value) { if (value != null) webView.webViewClient = value }
    var webChromeClient: WebChromeClient?
        get() = null
        set(value) { webView.webChromeClient = value }
    fun loadUrl(url: String) = webView.loadUrl(url)
    fun canGoBack(): Boolean = webView.canGoBack()
    fun goBack() = webView.goBack()
    fun stopLoading() = webView.stopLoading()
    fun clearHistory() = webView.clearHistory()
    fun removeAllViews() = webView.removeAllViews()
    fun destroy() = webView.destroy()
}

@Composable
internal fun ScreenFrame(
    toolbar: ComposeToolbarState? = null,
    scroll: Boolean = true,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFCF6), SiTahuColors.Screen, SiTahuColors.ScreenWarm)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(SiTahuColors.Primary.copy(alpha = 0.035f))
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.BottomStart)
                .clip(CircleShape)
                .background(SiTahuColors.Primary.copy(alpha = 0.025f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            if (toolbar != null) ToolbarView(toolbar)
            val base = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
            if (scroll) {
                Column(
                    modifier = base.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            } else {
                Column(
                    modifier = base,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            }
        }
        floatingAction?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) { it() }
        }
    }
}

@Composable
internal fun ToolbarView(toolbar: ComposeToolbarState) {
    if (!toolbar.isVisible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFFCF6).copy(alpha = 0.98f))
            .border(BorderStroke(1.dp, SiTahuColors.Line.copy(alpha = 0.85f)), RoundedCornerShape(20.dp))
            .padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (toolbar.showBack) {
            IconButton(
                onClick = { toolbar.onBack?.invoke() },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SiTahuColors.PrimarySoft)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.92f)), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = SiTahuColors.PrimaryDark)
            }
        } else {
            BrandMark(size = 38.dp, text = "ST")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toolbar.title.ifBlank { "SiTahu" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = SiTahuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            toolbar.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SiTahuColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp = 48.dp, text: String = "ST") {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2)))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.56f)), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text.take(2), color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NotificationBubble(text: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SiTahuColors.PrimarySoft)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("!", color = SiTahuColors.PrimaryDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(SiTahuColors.Danger)
                .border(BorderStroke(1.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text.take(1), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProfileBubble(initial: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.White, SiTahuColors.PrimarySoft)))
            .border(BorderStroke(2.dp, Color.White), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, fontWeight = FontWeight.Black, color = SiTahuColors.PrimaryDark, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(10.dp)
                .clip(CircleShape)
                .background(SiTahuColors.Green)
                .border(BorderStroke(2.dp, Color.White), CircleShape)
        )
    }
}

@Composable
private fun DecorativeOrb(modifier: Modifier, size: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    if (!visible) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SiTahuColors.Glass),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.97f), SiTahuColors.CardSoft.copy(alpha = 0.92f))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun HeroCard(title: String, subtitle: String? = null, trailing: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFC79A3D), Color(0xFFFFF3D6), Color(0xFFFFFCF6))
                    )
                )
                .padding(18.dp)
        ) {
            DecorativeOrb(Modifier.align(Alignment.TopEnd).offset(24.dp, (-26).dp), 124.dp, Color.White.copy(alpha = 0.26f))
            DecorativeOrb(Modifier.align(Alignment.BottomEnd).offset(34.dp, 22.dp), 72.dp, SiTahuColors.Primary2.copy(alpha = 0.18f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BrandMark(size = 58.dp, text = trailing ?: title.initials())
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, color = SiTahuColors.TextPrimary)
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = SiTahuColors.TextPrimary.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Si", style = MaterialTheme.typography.titleLarge, color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun String.initials(): String = split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "ST" }

@Composable
internal fun TextHandleView(handle: ComposeTextState, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    if (!handle.isVisible) return
    Text(
        text = handle.text.toString(),
        modifier = modifier.alpha(handle.alpha),
        color = SiTahuColors.TextPrimary,
        style = style
    )
}

@Composable
internal fun FieldView(handle: ComposeEditTextState, modifier: Modifier = Modifier) {
    if (!handle.isVisible) return
    val view = LocalView.current
    val shape = RoundedCornerShape(24.dp)
    val clickModifier = if (handle.hasClickListener) {
        Modifier.clickable(enabled = handle.isEnabled) { handle.performClick(view) }
    } else {
        Modifier
    }
    OutlinedTextField(
        value = handle.value,
        onValueChange = { handle.updateFromUi(it) },
        enabled = handle.isEnabled,
        label = { Text(handle.hint.ifBlank { "Input" }) },
        isError = handle.error != null,
        supportingText = { handle.error?.let { Text(it.toString()) } },
        keyboardOptions = KeyboardOptions(keyboardType = handle.keyboardType),
        singleLine = handle.singleLine,
        modifier = modifier
            .fillMaxWidth()
            .alpha(handle.alpha)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.86f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.70f)), shape)
            .then(clickModifier),
        shape = shape
    )
}

@Composable
internal fun ButtonView(handle: ComposeButtonState, modifier: Modifier = Modifier, primary: Boolean = true) {
    if (!handle.isVisible) return
    val view = LocalView.current
    val text = handle.text.toString().ifBlank { "Lanjut" }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (handle.isEnabled) handle.alpha else 0.55f)
            .clip(shape)
            .background(
                if (primary) Brush.horizontalGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.96f), SiTahuColors.CardSoft.copy(alpha = 0.96f)))
            )
            .border(
                BorderStroke(1.dp, if (primary) Color.White.copy(alpha = 0.35f) else SiTahuColors.Line),
                shape
            )
            .clickable(enabled = handle.isEnabled) { handle.performClick(view) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else SiTahuColors.PrimaryDark,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CheckView(handle: ComposeCheckState) {
    if (!handle.isVisible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.70f))
            .clickable(enabled = handle.isEnabled) { handle.updateChecked(!handle.isChecked) }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = handle.isChecked,
            onCheckedChange = { handle.updateChecked(it) },
            enabled = handle.isEnabled
        )
        Spacer(Modifier.width(8.dp))
        Text(handle.text, color = SiTahuColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DropdownView(handle: ComposeSpinnerState, label: String = "Pilihan", modifier: Modifier = Modifier) {
    if (!handle.isVisible) return
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(modifier = modifier.fillMaxWidth().alpha(handle.alpha)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.88f))
                .border(BorderStroke(1.dp, SiTahuColors.Line), shape)
                .clickable(enabled = handle.isEnabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(SiTahuColors.PrimarySoft), contentAlignment = Alignment.Center) {
                Text("⌄", color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = SiTahuColors.TextSecondary)
                Text(handle.selectedItem?.toString().orEmpty().ifBlank { "Pilih" }, color = SiTahuColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SiTahuColors.TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            handle.options.ifEmpty { listOf("-") }.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        handle.updateSelection(index)
                    }
                )
            }
        }
    }
}

@Composable
internal fun ProductPickerCard(container: ComposeComponent, label: ComposeTextState, leading: ComposeTextState, name: ComposeTextState, meta: ComposeTextState) {
    if (!container.isVisible) return
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(container.alpha)
            .clickable(enabled = container.isEnabled) { container.performClick(view) },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            Modifier
                .background(Brush.horizontalGradient(listOf(Color.White, SiTahuColors.PrimarySoft.copy(alpha = 0.72f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))),
                contentAlignment = Alignment.Center
            ) { Text(leading.text.toString().ifBlank { "P" }, fontWeight = FontWeight.Black, color = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(label.text.toString().ifBlank { "Produk" }, style = MaterialTheme.typography.labelMedium, color = SiTahuColors.TextSecondary)
                Text(name.text.toString().ifBlank { "Pilih produk" }, fontWeight = FontWeight.Black, color = SiTahuColors.TextPrimary)
                Text(meta.text.toString().ifBlank { "Ketuk untuk memilih" }, style = MaterialTheme.typography.bodySmall, color = SiTahuColors.TextSecondary)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun MetricCard(title: String, value: String, subtitle: String? = null) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SiTahuColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))),
                contentAlignment = Alignment.Center
            ) { Text(title.firstOrNull()?.uppercaseChar()?.toString().orEmpty(), color = Color.White, fontWeight = FontWeight.Black) }
            Text(title, color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = SiTahuColors.TextPrimary)
            subtitle?.takeIf { it.isNotBlank() }?.let { SmallPill(it, WarnaBaris.GREEN) }
        }
    }
}

@Composable
internal fun ListHandleView(handle: ComposeRecyclerState, modifier: Modifier = Modifier) {
    if (!handle.isVisible) return
    when (val adapter = handle.adapter) {
        is AdapterBarisUmum -> GenericRowList(adapter, modifier)
        is AdapterProduk -> ProductList(adapter, modifier)
        is AdapterKeranjang -> CartList(adapter, modifier)
        else -> EmptyState("Belum ada data yang ditampilkan")
    }
}

@Composable
private fun GenericRowList(adapter: AdapterBarisUmum, modifier: Modifier = Modifier) {
    val view = LocalView.current
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp, max = ((adapter.items.size.coerceIn(1, 5) * 108) + 12).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(adapter.items, key = { it.id + it.title + it.subtitle }) { item ->
            GenericRowCard(
                item = item,
                onClick = { adapter.onItemClick(item) },
                onAction = { adapter.onActionClick?.invoke(item, view) }
            )
        }
    }
}

@Composable
internal fun GenericRowsStatic(rows: List<ItemBaris>, onClick: (ItemBaris) -> Unit = {}, onAction: ((ItemBaris) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { item -> GenericRowCard(item, { onClick(item) }, onAction?.let { { it(item) } }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenericRowCard(item: ItemBaris, onClick: () -> Unit, onAction: (() -> Unit)? = null) {
    val tone = colorForTone(item.tone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color.White, SiTahuColors.CardSoft)))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(tone.first.copy(alpha = 0.90f), tone.first.copy(alpha = 0.64f))))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.70f)), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = SiTahuColors.TextPrimary)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.badge.isNotBlank()) SmallPill(item.badge, item.tone)
                    if (item.priceStatus.isNotBlank()) SmallPill(item.priceStatus, item.priceTone)
                    if (item.parameterStatus.isNotBlank()) SmallPill(item.parameterStatus, item.parameterTone)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.amount.isNotBlank()) Text(item.amount, fontWeight = FontWeight.Black, color = SiTahuColors.TextPrimary)
                if (onAction != null || !item.actionLabel.isNullOrBlank()) {
                    IconButton(
                        onClick = { if (onAction != null) onAction() else item.actionLabel?.let { } },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(SiTahuColors.PrimarySoft)
                    ) {
                        if (item.actionLabel.isNullOrBlank() || item.actionLabel == "⋮") Icon(Icons.Default.MoreVert, contentDescription = "Aksi", tint = SiTahuColors.PrimaryDark)
                        else Text(item.actionLabel.orEmpty(), color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SmallPill(text: String, tone: WarnaBaris = WarnaBaris.DEFAULT) {
    val colors = colorForTone(tone)
    Surface(shape = RoundedCornerShape(999.dp), color = colors.second, border = BorderStroke(1.dp, colors.first.copy(alpha = 0.10f))) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = colors.first,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun colorForTone(tone: WarnaBaris): Pair<Color, Color> = when (tone) {
    WarnaBaris.GREEN -> SiTahuColors.Green to SiTahuColors.GreenSoft
    WarnaBaris.GOLD -> SiTahuColors.PrimaryDark to SiTahuColors.PrimarySoft
    WarnaBaris.ORANGE -> SiTahuColors.Primary to SiTahuColors.PrimarySoft
    WarnaBaris.BLUE -> SiTahuColors.Primary to SiTahuColors.PrimarySoft
    WarnaBaris.RED -> SiTahuColors.Danger to SiTahuColors.DangerSoft
    WarnaBaris.DEFAULT -> SiTahuColors.TextSecondary to SiTahuColors.CardSoft
}

@Composable
private fun ProductList(adapter: AdapterProduk, modifier: Modifier = Modifier) {
    val rows = adapter.items.chunked(2)
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = ((rows.size.coerceIn(1, 3) * 214) + 14).dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { product ->
                    ProductCard(product, adapter, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProductCard(product: Produk, adapter: AdapterProduk, modifier: Modifier = Modifier) {
    val status = adapter.getStatus(product)
    val harga = adapter.getHarga(product)
    val stokLayakJual = product.safeStock + product.nearExpiredStock + product.edTodayStock
    val bisaDitambah = stokLayakJual > 0 && harga > 0L && status != "Habis" && status != "Kedaluwarsa"
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFCF6))))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFF3D6), Color(0xFFFFFFFF)))),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(SiTahuColors.PrimarySoft, Color.White))), contentAlignment = Alignment.Center) {
                    Text(product.name.initials().take(2), color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    SmallPill("Stok ${Formatter.ribuan(stokLayakJual.toLong())}", WarnaBaris.GREEN)
                }
            }
            Text(product.name, fontWeight = FontWeight.Black, color = SiTahuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Formatter.currency(harga), fontWeight = FontWeight.Bold, color = SiTahuColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill(product.category.ifBlank { "Produk" }, WarnaBaris.GOLD)
                Spacer(Modifier.weight(1f))
                FloatingActionButton(
                    onClick = { if (bisaDitambah) adapter.onAdd(product) },
                    modifier = Modifier.size(44.dp),
                    containerColor = if (bisaDitambah) SiTahuColors.Primary else SiTahuColors.Line,
                    contentColor = Color.White
                ) {
                    if (bisaDitambah) Icon(Icons.Default.Add, contentDescription = "Tambah") else Text("0", color = SiTahuColors.TextMuted)
                }
            }
            SmallPill(status, when (status) {
                "Produksi Hari Ini" -> WarnaBaris.GREEN
                "Stok Sisa" -> WarnaBaris.GOLD
                "ED Hari Ini" -> WarnaBaris.ORANGE
                "Hampir Kedaluwarsa" -> WarnaBaris.ORANGE
                "Kedaluwarsa", "Habis" -> WarnaBaris.RED
                else -> WarnaBaris.DEFAULT
            })
        }
    }
}

@Composable
private fun CartList(adapter: AdapterKeranjang, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp, max = ((adapter.items.size.coerceIn(1, 4) * 92) + 8).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(adapter.items, key = { it.productId }) { item ->
            CartRow(item, adapter)
        }
    }
}

@Composable
private fun CartRow(item: ItemKeranjang, adapter: AdapterKeranjang) {
    val product = adapter.getProduk(item.productId)
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(SiTahuColors.PrimarySoft), contentAlignment = Alignment.Center) {
                Text(product?.name?.initials()?.take(2) ?: "P", color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(product?.name ?: "Produk", fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Formatter.ribuan(item.qty.toLong())} x ${Formatter.currency(item.price)}", color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(Formatter.currency(item.qty.toLong() * item.price), fontWeight = FontWeight.Bold, color = SiTahuColors.PrimaryDark)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SiTahuColors.CardSoft)
                    .border(BorderStroke(1.dp, SiTahuColors.Line), RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { adapter.onDecrease(item) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Kurangi", tint = SiTahuColors.PrimaryDark) }
                Text(Formatter.ribuan(item.qty.toLong()), fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { adapter.onIncrease(item) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Tambah", tint = SiTahuColors.PrimaryDark) }
            }
            IconButton(onClick = { adapter.onRemove(item) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = SiTahuColors.Danger) }
        }
    }
}

@Composable
internal fun EmptyState(message: String) {
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(SiTahuColors.PrimarySoft), contentAlignment = Alignment.Center) {
                Text("✨", fontWeight = FontWeight.Black)
            }
            Text(message, color = SiTahuColors.TextSecondary, modifier = Modifier.padding(4.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun PaginationView(container: ComposeComponent, pageInfo: ComposeTextState, prev: ComposeButtonState, next: ComposeButtonState) {
    if (!container.isVisible) return
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { prev.performClick(view) }, enabled = prev.isEnabled, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.78f))) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Sebelumnya", tint = SiTahuColors.PrimaryDark) }
        SmallPill(pageInfo.text.toString().ifBlank { "Halaman" }, WarnaBaris.GOLD)
        IconButton(onClick = { next.performClick(view) }, enabled = next.isEnabled, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.78f))) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Berikutnya", tint = SiTahuColors.PrimaryDark) }
    }
}

@Composable
internal fun BitmapImageView(handle: ComposeImageState, modifier: Modifier = Modifier) {
    if (!handle.isVisible) return
    val view = LocalView.current
    handle.bitmap?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QRIS",
            modifier = modifier
                .size(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(12.dp)
                .clickable { handle.performClick(view) }
        )
    }
}

@Composable
internal fun WebViewBox(web: ComposeWebViewState, modifier: Modifier = Modifier) {
    if (!web.isVisible) return
    AndroidView(
        factory = { web.webView },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
    )
}

@Composable
internal fun PremiumChartCard(title: String, value: String, subtitle: String = "Performa terbaru") {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            SmallPill("Naik 12.8%", WarnaBaris.GREEN)
        }
        Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        MiniTrendChart(Modifier.fillMaxWidth().height(150.dp))
    }
}

@Composable
internal fun MiniTrendChart(modifier: Modifier = Modifier) {
    val points = listOf(0.12f, 0.22f, 0.28f, 0.24f, 0.48f, 0.38f, 0.58f, 0.70f, 0.62f, 0.86f, 0.68f, 0.76f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFFCF6), Color(0xFFFFFCF6))))) {
        val left = 18.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(Color(0xFFEBDDCA), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        val step = (right - left) / (points.size - 1)
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { index, value ->
            val x = left + step * index
            val y = bottom - (bottom - top) * value
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(right, bottom)
        fillPath.close()
        drawPath(fillPath, Brush.verticalGradient(listOf(SiTahuColors.Primary.copy(alpha = 0.32f), SiTahuColors.Primary.copy(alpha = 0.04f))))
        drawPath(path, SiTahuColors.Primary2, style = Stroke(width = 3.dp.toPx()))
        val highlightIndex = 7
        val hx = left + step * highlightIndex
        val hy = bottom - (bottom - top) * points[highlightIndex]
        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(hx, hy))
        drawCircle(SiTahuColors.Primary2, radius = 4.dp.toPx(), center = Offset(hx, hy))
    }
}
