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
import androidx.core.content.ContextCompat
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
        com.get.detail.rentdesk.notifications.RentReminderScheduler(this).ensureScheduled()

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        setupMainMenu()
        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.subtitle = null
            val isPropertyList = destination.id == R.id.propertyListFragment
            val isAddressList = destination.id == R.id.addressListFragment
            val isPropertyDetails = destination.id == R.id.propertyDetailsFragment
            val isTenantDetails = destination.id == R.id.tenantDetailsFragment
            val toolbarColor = ContextCompat.getColor(this, when {
                isAddressList -> R.color.address_page_background
                isPropertyList -> R.color.property_page_background
                isPropertyDetails -> R.color.property_detail_page
                isTenantDetails -> R.color.tenant_detail_page
                else -> R.color.app_surface
            })
            binding.toolbar.setBackgroundColor(toolbarColor)
            binding.root.setBackgroundColor(toolbarColor)
            (binding.toolbar.parent as android.view.View).setBackgroundColor(toolbarColor)
            binding.toolbar.setTitleTextAppearance(this, when {
                isAddressList -> R.style.TextAppearance_RentDesk_AddressToolbar
                isPropertyDetails -> R.style.TextAppearance_RentDesk_PropertyDetailToolbar
                isTenantDetails -> R.style.TextAppearance_RentDesk_TenantDetailToolbar
                else -> com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            })
            binding.toolbar.setTitleTextColor(ContextCompat.getColor(this, when {
                isAddressList -> R.color.address_heading
                isPropertyDetails -> R.color.property_detail_title
                isTenantDetails -> R.color.tenant_detail_title
                else -> R.color.text_primary
            }))
            binding.toolbar.setSubtitleTextAppearance(this, if (isPropertyDetails)
                R.style.TextAppearance_RentDesk_PropertyDetailSubtitle else R.style.TextAppearance_RentDesk_ToolbarSubtitle)
            binding.toolbar.setSubtitleTextColor(ContextCompat.getColor(this, if (isPropertyDetails)
                R.color.property_detail_secondary else R.color.text_secondary))
            invalidateOptionsMenu()
        }
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
