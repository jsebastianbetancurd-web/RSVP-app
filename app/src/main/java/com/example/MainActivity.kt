package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BookDatabase
import com.example.data.BookRepository
import com.example.ui.RsvpAppScreen
import com.example.ui.RsvpViewModel
import com.example.ui.RsvpViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = BookDatabase.getInstance(applicationContext)
        val repository = BookRepository(database.bookDao())

        setContent {
            MyApplicationTheme {
                val viewModel: RsvpViewModel = viewModel(
                    factory = RsvpViewModelFactory(repository)
                )
                RsvpAppScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

