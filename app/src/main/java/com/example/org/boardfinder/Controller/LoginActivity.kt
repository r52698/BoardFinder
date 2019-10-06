package com.example.org.boardfinder.Controller

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.Controller.MapsActivity.Companion.appState
import com.example.org.boardfinder.R
import com.example.org.boardfinder.Services.AuthService
import com.example.org.boardfinder.Utilities.BROADCAST_USER_DATA_CHANGE
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        loginSpinner.visibility = View.INVISIBLE
    }

    fun loginLoginBtnClicked (view: View) {
        enableSpinner(true)
        val email = loginEmailText.text.toString()
        val password = loginPasswordText.text.toString()
        hideKeyboard()
        if (email.isNotEmpty() && password.isNotEmpty()) {
            println("Trying to login email $email password $password")
            AuthService.loginUser(email, password) { loginSuccess ->
                if (loginSuccess) {
                    println("Login completed successfully")
                    AuthService.findUserByEmail(this) { findUserSuccess ->
                        if (findUserSuccess) {
                            println("Find user by email completed successfully")
                            val userDataChange = Intent(BROADCAST_USER_DATA_CHANGE)
                            LocalBroadcastManager.getInstance(this).sendBroadcast(userDataChange)
                            println("Login handshake: quitting LoginActivity")
                            finish()
                        } else {
                            errorToast()
                            println("The find by email attempt was not successful")
                        }
                    }
                } else {
                    println("The login attempt was not successful")
                    errorToast()
                }
            }
        } else {
            Toast.makeText(this, "Please fill in email and password.", Toast.LENGTH_LONG).show()
            enableSpinner(false)
        }
    }

    fun errorToast() {
        Toast.makeText(this, "Something went wrong, please try again.", Toast.LENGTH_LONG).show()
        enableSpinner(false)
    }

    fun enableSpinner(enable: Boolean) {
        if (enable) {
            loginSpinner.visibility = View.VISIBLE
        } else {
            loginSpinner.visibility = View.INVISIBLE
        }
        loginLoginBtn.isEnabled = !enable
        loginCreateUSerBtn.isEnabled = !enable
    }

    fun hideKeyboard() {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (inputManager.isAcceptingText) {
            inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }

    fun loginCreateUserBtnClicked (view: View) {
        val createUserIntent = Intent(this, CreateUserActivity::class.java)
        startActivity(createUserIntent)
        finish()
    }
}