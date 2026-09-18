package pl.lejdi.plannerkmp.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing

/**
 * The form primitives both feature screens need, in the module that owns the conventions they
 * encode.
 *
 * Each of these lived private inside one screen file while the *other* screen either re-derived it
 * or went without. That is the same drift `LoadableContent` and `MessageHost` were pulled up here
 * to stop: a rule about how this app renders an invalid field, or how tall a tappable input has to
 * be, is not one feature's business — and the copy that stayed behind is the one that stops
 * matching.
 */

/**
 * The error line under an invalid input.
 *
 * Takes its text already resolved: *what* is wrong belongs to the feature that knows the rule, the
 * way it looks belongs here.
 */
@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/**
 * A borderless single- or multi-line text input for editing in place, inside a card or a row.
 *
 * Built on [BasicTextField] plus Material's own decoration box rather than on `TextField`, because
 * the inline editors want the indicator and the placeholder without the filled container. Two
 * details here are corrections, not styling, and are the reason this is a shared component rather
 * than a screen's private helper:
 *
 *  - `DecorationBox` applies none of the height the `TextField` composable does, so a field built
 *    this way is one text line plus 4dp — roughly half a touch target. [Sizing.minimumTouchTarget]
 *    puts it back.
 *  - [isError] has to be forwarded to `DecorationBox` as well as used for the text colour. Left at
 *    its default the box drew the ordinary indicator under an invalid value, so the underline and
 *    the text disagreed about whether anything was wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    /**
     * Defaulted so the text editors that had no opinion keep the keyboard they had.
     *
     * A field that takes only digits has to be able to ask for a digit keypad: an inline editor
     * whose whole reason for existing is "type a number without leaving the list" is not easy if
     * reaching the number takes two taps on a letter keyboard first.
     */
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizing.minimumTouchTarget),
        visualTransformation = VisualTransformation.None,
        interactionSource = interactionSource,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = if (isError) textStyle.copy(color = MaterialTheme.colorScheme.error) else textStyle,
    ) { innerTextField ->
        PlainFieldDecoration(
            text = value,
            placeholder = placeholder,
            textStyle = textStyle,
            singleLine = singleLine,
            isError = isError,
            interactionSource = interactionSource,
            innerTextField = innerTextField,
        )
    }
}

/**
 * The same field, driven by a [TextFieldValue] so the caller owns the cursor.
 *
 * The `String` overload cannot: `BasicTextField` seeds its internal state with
 * `TextFieldValue(text = value)`, whose selection defaults to `TextRange(0)`, so a field that opens
 * with text already in it puts the caret *before* it and typing prepends. That is wrong for an
 * inline editor opened on an existing value, where the user means to append or replace — and
 * nothing short of this overload can move it, because the String API never exposes the selection.
 *
 * Callers seed the selection once, when the field opens:
 * `remember(key) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }`.
 */
@Composable
fun PlainTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizing.minimumTouchTarget),
        visualTransformation = VisualTransformation.None,
        interactionSource = interactionSource,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = if (isError) textStyle.copy(color = MaterialTheme.colorScheme.error) else textStyle,
    ) { innerTextField ->
        PlainFieldDecoration(
            text = value.text,
            placeholder = placeholder,
            textStyle = textStyle,
            singleLine = singleLine,
            isError = isError,
            interactionSource = interactionSource,
            innerTextField = innerTextField,
        )
    }
}

/**
 * The decoration both overloads share, so the two corrections above are made in one place rather
 * than copied into the second one and left to drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainFieldDecoration(
    text: String,
    placeholder: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    isError: Boolean,
    interactionSource: MutableInteractionSource,
    innerTextField: @Composable () -> Unit,
) {
    TextFieldDefaults.DecorationBox(
        value = text,
        visualTransformation = VisualTransformation.None,
        innerTextField = innerTextField,
        singleLine = singleLine,
        enabled = true,
        isError = isError,
        interactionSource = interactionSource,
        // Horizontal inset too: zeroing it put the text and placeholder flush against the
        // card's own padding edge.
        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.sm),
        placeholder = { Text(text = placeholder, style = textStyle) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSecondaryContainer
                .copy(alpha = UNFOCUSED_INDICATOR_ALPHA),
            errorIndicatorColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** How far the resting underline is faded relative to the focused one. */
private const val UNFOCUSED_INDICATOR_ALPHA = 0.4f
