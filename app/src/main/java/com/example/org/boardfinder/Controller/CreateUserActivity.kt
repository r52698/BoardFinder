package com.example.org.boardfinder.Controller

import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.R
import com.example.org.boardfinder.Services.AuthService
import com.example.org.boardfinder.Services.UserDataService
import com.example.org.boardfinder.Utilities.BROADCAST_USER_DATA_CHANGE
import kotlinx.android.synthetic.main.activity_create_user.*
import java.util.*

class CreateUserActivity : AppCompatActivity() {

    var userAvatar = "profileDefault"
    var avatarColor = "[0.5, 0.5, 0.5, 1]"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_user)
        spinner.visibility = View.INVISIBLE
    }

    fun generateUserAvatar(view: View) {
        val random = Random()
        val color = random.nextInt(2)
        val avatar = random.nextInt(28)

        if (color == 0) {
            userAvatar = "light$avatar"
        } else {
            userAvatar = "dark$avatar"
        }

        val resourceId = resources.getIdentifier(userAvatar, "drawable", packageName)
        createAvatarImageView.setImageResource(resourceId)
    }

    fun generateColorClicked(view: View) {
        val random = Random()
        val r = random.nextInt(256)
        val g = random.nextInt(256)
        val b = random.nextInt(256)
        createAvatarImageView.setBackgroundColor(Color.rgb(r, g, b))
        avatarColor = "[${r/256.0}, ${g/256.0}, ${b/256.0}, 1]"
        println(avatarColor)
    }

    fun createUserClicked(view: View) {
        enableSpinner(true)
        val userName = "${createUserNameText.text}^${createPhoneNumberText.text}"
        val email = createEmailText.text.toString()
        val password = createPasswordText.text.toString()
        if (userName.isNotEmpty() && email.isValidEmail() && password.isNotEmpty()) {
            AuthService.registerUser(email, password) { registerSuccess ->
                if (registerSuccess) {
                    println("Create user completed successfully")
                    AuthService.loginUser(email, password) { loginSuccess ->
                        if (loginSuccess) {
                            println("CUR Login user completed successfully")
                            println("Token=${App.prefs.authToken}, Email=${App.prefs.userEmail}")
                            AuthService.createUser(
                                userName,
                                email,
                                userAvatar,
                                avatarColor
                            ) { createSuccess ->
                                if (createSuccess) {
                                    val userDataChange = Intent(BROADCAST_USER_DATA_CHANGE)
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(userDataChange)

                                    println(UserDataService.name)
                                    println(UserDataService.email)
                                    println(UserDataService.avatarName)
                                    println(UserDataService.avatarColor)
                                    println(UserDataService.id)
                                    finish()
                                } else {
                                    errorToast()
                                    println("CUR Create user unsuccessful")
                                }
                            }
                        } else {
                            errorToast()
                            println("CUR Login unsuccessful")
                        }
                    }
                } else {
                    errorToast()
                    println("CUR Register user unsuccessful")
                }
            }
        } else {
            Toast.makeText(this, "Make sure user name, email, password are filled in.", Toast.LENGTH_LONG).show()
            enableSpinner(false)
        }
    }

    fun String.isValidEmail() =
        this.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

    fun errorToast() {
        Toast.makeText(this, "Something went wrong, please try again.", Toast.LENGTH_LONG).show()
        enableSpinner(false)
    }

    fun enableSpinner(enable: Boolean) {
        if (enable) {
            spinner.visibility = View.VISIBLE
        } else {
            spinner.visibility = View.INVISIBLE
        }
        createUserBtn.isEnabled = !enable
        backgroundColorBtn.isEnabled = !enable
        createAvatarImageView.isEnabled = !enable
    }
}
