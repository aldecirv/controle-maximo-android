package com.controlemaximo.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.controlemaximo.app.R
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.databinding.ActivityMainBinding
import com.controlemaximo.app.ui.fragments.AlertsFragment
import com.controlemaximo.app.ui.fragments.DashboardFragment
import com.controlemaximo.app.ui.fragments.MovementsFragment
import com.controlemaximo.app.ui.fragments.RegisterFragment
import com.controlemaximo.app.util.ThemeUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: InventoryDb
    private var selected = R.id.navDashboard
    private var moreSheet: MoreSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.lightSystemBars(this)
        db = InventoryDb(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.navDashboard.setOnClickListener { select(R.id.navDashboard) }
        binding.navRegister.setOnClickListener { select(R.id.navRegister) }
        binding.navAlerts.setOnClickListener { select(R.id.navAlerts) }
        binding.navStock.setOnClickListener { select(R.id.navStock) }
        binding.navMore.setOnClickListener { showMoreSheet() }
        binding.moreButton.setOnClickListener { showMoreSheet() }
        binding.bellButton.setOnClickListener { select(R.id.navAlerts) }
        select(R.id.navDashboard)
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    fun refreshHeader() {
        val settings = db.settings()
        binding.storeName.text = settings.storeName
        val urgent = db.alerts().count { it.level != com.controlemaximo.app.data.AlertLevel.ATTENTION }
        binding.urgentBadge.visibility = if (urgent > 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.urgentBadge.text = urgent.toString()
    }

    fun select(id: Int) {
        selected = id
        val fragment: Fragment = when (id) {
            R.id.navRegister -> RegisterFragment()
            R.id.navAlerts -> AlertsFragment()
            R.id.navStock -> MovementsFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.content, fragment)
            .commit()
        updateNav()
        refreshHeader()
    }

    fun openAlerts() = select(R.id.navAlerts)

    fun openMovement(lotId: String? = null) {
        selected = R.id.navStock
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.content, MovementsFragment.newInstance(lotId))
            .commit()
        updateNav()
        refreshHeader()
    }

    fun openShowcase() = startActivity(Intent(this, ShowcaseActivity::class.java))

    private fun showMoreSheet() {
        if (moreSheet?.isAdded == true) return
        moreSheet = MoreSheet().also { it.show(supportFragmentManager, "more") }
    }

    private fun updateNav() {
        val icons = listOf(binding.navDashboardIcon, binding.navRegisterIcon, binding.navAlertsIcon, binding.navStockIcon, binding.navMoreIcon)
        val labels = listOf(binding.navDashboardLabel, binding.navRegisterLabel, binding.navAlertsLabel, binding.navStockLabel, binding.navMoreLabel)
        val ids = listOf(R.id.navDashboard, R.id.navRegister, R.id.navAlerts, R.id.navStock, R.id.navMore)
        icons.forEachIndexed { i, icon ->
            val on = ids[i] == selected
            icon.setColorFilter(ContextCompat.getColor(this, if (on) R.color.primary else R.color.text_tertiary))
            labels[i].setTextColor(ContextCompat.getColor(this, if (on) R.color.primary else R.color.text_tertiary))
            val parent = icon.parent as android.view.View
            parent.setBackgroundResource(if (on) R.drawable.bg_nav_active else android.R.color.transparent)
        }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}

interface Refreshable { fun refresh() }
