package com.alphawallet.app.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alphawallet.app.listener.OnClickSpanListener


fun setSpannable(
    context: Context,
    textView: TextView, textFirst: String,
    textLast: String,
    colorSpanStart: Int,
    colorSpanEnd: Int,
    interfaceClickSpan: OnClickSpanListener,
) {
    val builder = SpannableStringBuilder()

    val textBlack =
        SpannableString(textFirst)

    textBlack.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, colorSpanStart)),
        0,
        textBlack.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append(textBlack)

    val clickableSpan = object : ClickableSpan() {
        override fun onClick(p0: View) {
            interfaceClickSpan.clickSpan()
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = false
            ds.color = ContextCompat.getColor(context, colorSpanEnd)
        }
    }

    val textGraySlotTotal =
        SpannableString(textLast)

    textGraySlotTotal.setSpan(
        clickableSpan,
        0,
        textGraySlotTotal.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append(textGraySlotTotal)

    textView.setText(builder, TextView.BufferType.SPANNABLE)
    textView.movementMethod = LinkMovementMethod.getInstance();

}

fun setSpannableWithLine(
    context: Context,
    textView: TextView, textFirst: String,
    textLast: String,
    colorSpanStart: Int,
    colorSpanEnd: Int,
    interfaceClickSpan: OnClickSpanListener,
) {
    val builder = SpannableStringBuilder()

    val textBlack =
        SpannableString(textFirst)

    textBlack.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, colorSpanStart)),
        0,
        textBlack.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append(textBlack)

    val clickableSpan = object : ClickableSpan() {
        override fun onClick(p0: View) {
            interfaceClickSpan.clickSpan()
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = true
            ds.color = ContextCompat.getColor(context, colorSpanEnd)
        }
    }

    val textGraySlotTotal =
        SpannableString(textLast)

    textGraySlotTotal.setSpan(
        clickableSpan,
        0,
        textGraySlotTotal.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append(textGraySlotTotal)

    textView.setText(builder, TextView.BufferType.SPANNABLE)
    textView.movementMethod = LinkMovementMethod.getInstance();

}
