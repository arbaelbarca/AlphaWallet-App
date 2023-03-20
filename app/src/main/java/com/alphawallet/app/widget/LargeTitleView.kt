package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import android.view.LayoutInflater
import com.alphawallet.app.R

class LargeTitleView(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    val title: TextView
    val subtitle: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_large_title_view, this, true)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
    }
}
