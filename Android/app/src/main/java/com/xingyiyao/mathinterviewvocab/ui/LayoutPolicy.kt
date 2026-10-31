package com.xingyiyao.mathinterviewvocab.ui

import com.xingyiyao.mathinterviewvocab.core.Term

enum class AppWindowLayout { Compact, Medium, Expanded }

const val MediumWidthDp = 600
const val ExpandedWidthDp = 840

fun layoutForWidth(widthDp: Float): AppWindowLayout {
    require(widthDp.isFinite() && widthDp >= 0) { "窗口宽度必须是非负有限数。" }
    return when {
        widthDp < MediumWidthDp -> AppWindowLayout.Compact
        widthDp < ExpandedWidthDp -> AppWindowLayout.Medium
        else -> AppWindowLayout.Expanded
    }
}

fun voicePreviewText(terms: List<Term>): String? = terms.firstOrNull { it.id == 1 }?.speechText
