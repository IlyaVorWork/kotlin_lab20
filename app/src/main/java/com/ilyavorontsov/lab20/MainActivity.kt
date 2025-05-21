package com.ilyavorontsov.lab20

import android.app.Notification.EXTRA_NOTIFICATION_ID
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*

object ColorVM : ViewModel() {
    private val selectedColor = MutableLiveData<String>()
    val SelectedColor: LiveData<String> get() = selectedColor

    fun selectColor(item: String) {
        selectedColor.value = item
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var showNotificationBtn: Button
    private lateinit var bgColorTV: TextView
    private lateinit var layout: ConstraintLayout

    private val colorVM: ColorVM by viewModels()

    private val colorBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("My Broadcast Receiver", "Получена рассылка ${intent.action}")
            when (intent.action) {
                "ru.ilyavorontsov_lab20.set_color" -> {
                    val bg =
                        RemoteInput.getResultsFromIntent(intent)?.getCharSequence("NEW_COLOR").toString()
                    ColorVM.selectColor(bg)
                    notificationManager.cancel(1)
                }

                "ru.ilyavorontsov_lab20.reset_color" -> {
                    ColorVM.selectColor("FFFFFF")
                }
            }
        }
    }

    private lateinit var notificationManager: NotificationManagerCompat

    private val requestResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ContextCompat.registerReceiver(this, colorBroadcastReceiver, android.content.IntentFilter().apply {
            addAction("ru.ilyavorontsov_lab20.set_color")
            addAction("ru.ilyavorontsov_lab20.reset_color")
        }, ContextCompat.RECEIVER_EXPORTED)

        notificationManager = NotificationManagerCompat.from(this)

        showNotificationBtn = findViewById(R.id.showNotificationButton)
        bgColorTV = findViewById(R.id.bgColorTV)
        layout = findViewById(R.id.main)

        bgColorTV.text = String.format(
            getString(R.string.selected_color), String.format("#%06X", (0xFFFFFF and getColor(R.color.white)))
        );
        layout.setBackgroundColor(getColor(R.color.white))

        showNotificationBtn.setOnClickListener {
            showNotification()
        }

        colorVM.SelectedColor.observe(this) { color ->
            val hexColor = "#" + color
            bgColorTV.text = String.format(
                getString(R.string.selected_color), hexColor
            )
            layout.setBackgroundColor(hexColor.toColorInt())
        }
    }

    private fun showNotification() {
        val CHANNEL_MSG_ID = "lab20_channel_msg"
        val channel = NotificationChannel(
            CHANNEL_MSG_ID, "Сообщения", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Уведомление о входящих сообщениях"
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder("NEW_COLOR").run {
            setLabel("Цвет в формате RRGGBB")
            build()
        }

        val setColorIntent = Intent("ru.ilyavorontsov_lab20.set_color")
        setColorIntent.setPackage(packageName)

        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                PendingIntent.FLAG_MUTABLE

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                PendingIntent.FLAG_IMMUTABLE

            else ->
                PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                1,
                setColorIntent,
                flags
            )

        val changeColorAction = NotificationCompat.Action.Builder(R.drawable.palette_48px, "ЗАДАТЬ ЦВЕТ", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        val resetColorActionIntent = Intent("ru.ilyavorontsov_lab20.reset_color").apply {
            putExtra(EXTRA_NOTIFICATION_ID, 1)
        }

        val resetColorPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            resetColorActionIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_MSG_ID)
            .setSmallIcon(R.drawable.palette_48px)
            .setContentTitle("Управляющий")
            .setContentText("Отсюда можно управлять цветом фона программы")
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.palette_48px, "СБРОСИТЬ ЦВЕТ", resetColorPendingIntent)
            .addAction(changeColorAction)
            .setAutoCancel(true)
        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 и выше
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestResult.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationManager.notify(1, notification)
            }
        } else notificationManager.notify(1, notification)

    }
}