package com.get.detail.rentdesk.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.ActivityMainBinding
import com.get.detail.rentdesk.lock.AppLockManager
import com.get.detail.rentdesk.lock.AppLockSession
import com.get.detail.rentdesk.ui.lock.AppLockActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var lockManager: AppLockManager
    private var lockActivityLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lockManager = AppLockManager(this)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        setupMainMenu()
        navController.addOnDestinationChangedListener { _, _, _ -> invalidateOptionsMenu() }
    }

    override fun onStart() {
        super.onStart()
        updateSecureWindow()
        if (AppLockSession.isInternalAuthentication()) return

        if (lockManager.isEnabled && AppLockSession.requiresUnlock(lockManager.timeoutMillis)) {
            lockActivityLaunched = true
            startActivity(
                Intent(this, AppLockActivity::class.java)
                    .putExtra(AppLockActivity.EXTRA_RETURN_TO_CALLER, true)
            )
        } else {
            lockActivityLaunched = false
            AppLockSession.markForegrounded()
        }
    }

    override fun onStop() {
        if (
            lockManager.isEnabled &&
            !isChangingConfigurations &&
            !lockActivityLaunched &&
            !AppLockSession.isInternalAuthentication()
        ) {
            AppLockSession.markBackgrounded()
        }
        super.onStop()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun setupMainMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.main_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val hiddenDestinations = setOf(
                    R.id.loginFragment,
                    R.id.settingsFragment,
                    R.id.appLockSetupFragment
                )
                menu.findItem(R.id.action_settings)?.isVisible =
                    navController.currentDestination?.id !in hiddenDestinations
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == R.id.action_settings) {
                    navController.navigate(R.id.settingsFragment)
                    true
                } else {
                    false
                }
            }
        })
    }

    private fun updateSecureWindow() {
        if (lockManager.isEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
