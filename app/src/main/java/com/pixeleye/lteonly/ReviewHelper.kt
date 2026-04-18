package com.pixeleye.lteonly

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewHelper {
    fun askForReview(context: Context) {
        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                if (context is Activity) {
                    val flow = manager.launchReviewFlow(context, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        // The flow has finished. The API does not indicate whether the user
                        // reviewed or not, or even whether the review dialog was shown.
                    }
                }
            }
        }
    }
}
