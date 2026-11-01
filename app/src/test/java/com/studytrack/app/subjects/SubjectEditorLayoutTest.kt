package com.studytrack.app.subjects

import android.app.Application
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.studytrack.app.R
import com.studytrack.app.databinding.DialogAddEditSubjectBinding
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h640dp")
class SubjectEditorLayoutTest {
    @Test fun `save and cancel remain full width on a narrow screen`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_StudyTrack)
        controller.create().start().resume()
        try {
            val binding = DialogAddEditSubjectBinding.inflate(activity.layoutInflater)
            val density = activity.resources.displayMetrics.density
            val width = (288 * density).toInt()
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val minButtonWidth = (200 * density).toInt()
            assertTrue("Save button must be readable", binding.saveButton.measuredWidth >= minButtonWidth)
            assertTrue("Cancel button must be readable", binding.cancelButton.measuredWidth >= minButtonWidth)
            assertTrue("Name field must have room", binding.nameLayout.measuredWidth >= minButtonWidth)
            assertTrue("Description field must have room", binding.descriptionLayout.measuredWidth >= minButtonWidth)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
