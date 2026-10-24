package me.weishu.kernelsu.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.DisguiseConfig
import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_ENTRY_LENGTH = 16
private const val RESULT_SCALE = 12

private enum class Operator(val symbol: String) {
    DIVIDE("\u00f7"),
    MULTIPLY("\u00d7"),
    SUBTRACT("\u2212"),
    ADD("+"),
}

private enum class KeyKind { SYSTEM, DIGIT, OPERATOR, EQUALS }

/** Immediate-execution calculator state: each operator folds the previous result right away. */
private class CalculatorState {

    var display by mutableStateOf("0")
        private set

    var isError by mutableStateOf(false)
        private set

    private var accumulator by mutableStateOf<BigDecimal?>(null)
    private var pending by mutableStateOf<Operator?>(null)
    private var awaitingOperand = false
    private var justEvaluated = false
    private var usedOperator = false

    /** Small line above the main display, e.g. `"12 +"`. */
    val expression: String
        get() {
            val left = accumulator ?: return ""
            val operator = pending ?: return ""
            return "${left.pretty()} ${operator.symbol}"
        }

    fun clear() {
        display = "0"
        isError = false
        accumulator = null
        pending = null
        awaitingOperand = false
        justEvaluated = false
        usedOperator = false
    }

    fun inputDigit(digit: Char) {
        if (isError) clear()
        if (justEvaluated) {
            display = digit.toString()
            justEvaluated = false
            usedOperator = false
            return
        }
        if (awaitingOperand) {
            display = digit.toString()
            awaitingOperand = false
            return
        }
        when {
            display == "0" -> display = digit.toString()
            display == "-0" -> display = "-$digit"
            display.replace("-", "").length < MAX_ENTRY_LENGTH -> display += digit
        }
    }

    fun inputDot() {
        if (isError) clear()
        if (justEvaluated) {
            display = "0."
            justEvaluated = false
            usedOperator = false
            return
        }
        if (awaitingOperand) {
            display = "0."
            awaitingOperand = false
            return
        }
        if (!display.contains('.')) display += "."
    }

    fun toggleSign() {
        if (isError) return
        if (awaitingOperand) {
            display = "-0"
            awaitingOperand = false
            return
        }
        display = when {
            display.startsWith("-") -> display.drop(1)
            display == "0" -> display
            else -> "-$display"
        }
    }

    fun backspace() {
        if (isError) {
            clear()
            return
        }
        if (awaitingOperand || justEvaluated) return
        display = display.dropLast(1).ifEmpty { "0" }
        if (display == "-") display = "-0"
    }

    fun percent() {
        if (isError || awaitingOperand) return
        val value = parsedDisplay() ?: return
        display = value.divide(BigDecimal.valueOf(100)).pretty()
    }

    fun inputOperator(operator: Operator) {
        if (isError) return
        usedOperator = true
        if (awaitingOperand) {
            pending = operator
            return
        }
        val current = parsedDisplay() ?: return
        if (pending != null && accumulator != null) {
            val result = compute(accumulator!!, pending!!, current) ?: run {
                setError()
                return
            }
            accumulator = result
            display = result.pretty()
        } else {
            accumulator = current
        }
        pending = operator
        awaitingOperand = true
        justEvaluated = false
    }

    /** Evaluates the pending operation, or opens the manager when the passcode was entered. */
    fun evaluate(passcode: String, onUnlocked: () -> Unit) {
        if (isError) return
        val current = parsedDisplay() ?: return
        if (pending == null && accumulator == null && !usedOperator && display == passcode) {
            clear()
            onUnlocked()
            return
        }
        if (pending != null && accumulator != null) {
            val result = compute(accumulator!!, pending!!, current) ?: run {
                setError()
                return
            }
            display = result.pretty()
        } else {
            display = current.pretty()
        }
        accumulator = null
        pending = null
        awaitingOperand = false
        justEvaluated = true
    }

    private fun setError() {
        display = "0"
        isError = true
        accumulator = null
        pending = null
        awaitingOperand = false
        justEvaluated = false
        usedOperator = false
    }

    private fun parsedDisplay(): BigDecimal? {
        val raw = display.removeSuffix(".")
        return try {
            BigDecimal(raw)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun compute(left: BigDecimal, operator: Operator, right: BigDecimal): BigDecimal? =
        when (operator) {
            Operator.DIVIDE ->
                if (right.signum() == 0) null else left.divide(right, RESULT_SCALE, RoundingMode.HALF_UP)

            Operator.MULTIPLY -> left.multiply(right)
            Operator.SUBTRACT -> left.subtract(right)
            Operator.ADD -> left.add(right)
        }
}

private fun BigDecimal.pretty(): String {
    val rounded = if (scale() > RESULT_SCALE) setScale(RESULT_SCALE, RoundingMode.HALF_UP) else this
    val stripped = rounded.stripTrailingZeros()
    return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
}

@Composable
fun CalculatorScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { CalculatorState() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                val expression = state.expression
                if (expression.isNotEmpty()) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val displayText = if (state.isError) {
                    stringResource(R.string.calculator_error)
                } else {
                    state.display
                }
                Text(
                    text = displayText,
                    fontSize = displayFontSize(displayText),
                    fontWeight = FontWeight.Light,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Keypad(
            state = state,
            onUnlocked = onUnlocked,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f),
        )
    }
}

private fun displayFontSize(text: String): TextUnit = when {
    text.length <= 9 -> 64.sp
    text.length <= 13 -> 48.sp
    else -> 34.sp
}

@Composable
private fun Keypad(
    state: CalculatorState,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        KeyRow(Modifier.weight(1f)) {
            Key("C", KeyKind.SYSTEM, Modifier.weight(1f)) { state.clear() }
            KeyIcon(Icons.AutoMirrored.Filled.Backspace, KeyKind.SYSTEM, Modifier.weight(1f)) { state.backspace() }
            Key("%", KeyKind.SYSTEM, Modifier.weight(1f)) { state.percent() }
            Key("\u00f7", KeyKind.OPERATOR, Modifier.weight(1f)) { state.inputOperator(Operator.DIVIDE) }
        }
        KeyRow(Modifier.weight(1f)) {
            Key("7", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('7') }
            Key("8", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('8') }
            Key("9", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('9') }
            Key("\u00d7", KeyKind.OPERATOR, Modifier.weight(1f)) { state.inputOperator(Operator.MULTIPLY) }
        }
        KeyRow(Modifier.weight(1f)) {
            Key("4", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('4') }
            Key("5", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('5') }
            Key("6", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('6') }
            Key("\u2212", KeyKind.OPERATOR, Modifier.weight(1f)) { state.inputOperator(Operator.SUBTRACT) }
        }
        KeyRow(Modifier.weight(1f)) {
            Key("1", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('1') }
            Key("2", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('2') }
            Key("3", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('3') }
            Key("+", KeyKind.OPERATOR, Modifier.weight(1f)) { state.inputOperator(Operator.ADD) }
        }
        KeyRow(Modifier.weight(1f)) {
            Key("\u00b1", KeyKind.SYSTEM, Modifier.weight(1f)) { state.toggleSign() }
            Key("0", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDigit('0') }
            Key(".", KeyKind.DIGIT, Modifier.weight(1f)) { state.inputDot() }
            Key("=", KeyKind.EQUALS, Modifier.weight(1f)) {
                state.evaluate(DisguiseConfig.readPasscode(), onUnlocked)
            }
        }
    }
}

@Composable
private fun KeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun Key(
    label: String,
    kind: KeyKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeySurface(kind, modifier, onClick) {
        Text(
            text = label,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun KeyIcon(
    icon: ImageVector,
    kind: KeyKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeySurface(kind, modifier, onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun KeySurface(
    kind: KeyKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = when (kind) {
        KeyKind.SYSTEM -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer

        KeyKind.DIGIT -> MaterialTheme.colorScheme.surfaceContainerHigh to
                MaterialTheme.colorScheme.onSurface

        KeyKind.OPERATOR -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        KeyKind.EQUALS -> MaterialTheme.colorScheme.primary to
                MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .fillMaxSize(),
        shape = RoundedCornerShape(36.dp),
        color = colors.first,
        contentColor = colors.second,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
