package com.alphawallet.app.util

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

object MyBlurBuilder {
    fun applyBlur(pContext: Context?, pSrcBitmap: Bitmap?, pBlurRadius: Float): Bitmap? {
        return if (pSrcBitmap != null) {
            val copyBitmap = pSrcBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val outputBitmap = Bitmap.createBitmap(copyBitmap)
            val renderScript = RenderScript.create(pContext)
            val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(
                renderScript,
                Element.U8_4(renderScript)
            )
            val allocationIn = Allocation.createFromBitmap(renderScript, pSrcBitmap)
            val allocationOut = Allocation.createFromBitmap(renderScript, outputBitmap)
            scriptIntrinsicBlur.setRadius(pBlurRadius)
            scriptIntrinsicBlur.setInput(allocationIn)
            scriptIntrinsicBlur.forEach(allocationOut)
            allocationOut.copyTo(outputBitmap)
            outputBitmap
        } else {
            null
        }
    }
}
