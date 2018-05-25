package com.denisvengrin.slackgc.util

import android.view.View
import android.view.ViewPropertyAnimator


fun View.prepareVisibilityAnimation(visibility: Int): ViewPropertyAnimator {
    val show = visibility == View.VISIBLE
    return animate().apply {
        duration = 100
        alpha(if (show) 1f else 0f)

        val action = { this@prepareVisibilityAnimation.visibility = visibility }
        if (show) {
            withStartAction(action)
        } else {
            withEndAction(action)
        }
    }
}

fun View.startVisibilityAnimation(visibility: Int) {
    prepareVisibilityAnimation(visibility).start()
}