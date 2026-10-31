package com.xingyiyao.mathinterviewvocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xingyiyao.mathinterviewvocab.ui.AcademicTheme
import com.xingyiyao.mathinterviewvocab.ui.VocabularyApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AcademicTheme { VocabularyApp(viewModel) } }
    }

    override fun onStop() {
        viewModel.stopSpeech()
        super.onStop()
    }
}
