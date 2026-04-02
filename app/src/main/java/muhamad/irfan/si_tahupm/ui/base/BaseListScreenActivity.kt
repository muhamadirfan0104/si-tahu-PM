package muhamad.irfan.si_tahupm.ui.base

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.main.SimpleItemSelectedListener
import muhamad.irfan.si_tahupm.util.RowItem

abstract class BaseListScreenActivity : BaseActivity() {
    protected lateinit var binding: ActivityListScreenBinding
    protected lateinit var rowAdapter: GenericRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        rowAdapter = GenericRowAdapter(onItemClick = ::onRowClick, onActionClick = ::onRowAction)
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = rowAdapter
        binding.etSearch.addTextChangedListener { onSearchChanged() }
    }

    protected fun configureScreen(title: String, subtitle: String? = null, searchHint: String = "Cari data...") {
        bindToolbar(binding.toolbar, title, subtitle)
        binding.etSearch.hint = searchHint
    }

    protected fun setPrimaryFilter(options: List<String>, selectedIndex: Int = 0, onChange: () -> Unit) {
        binding.spPrimaryFilter.isVisible = true
        binding.spPrimaryFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spPrimaryFilter.setSelection(selectedIndex)
        binding.spPrimaryFilter.onItemSelectedListener = SimpleItemSelectedListener(onChange)
    }

    protected fun setSecondaryFilter(options: List<String>, selectedIndex: Int = 0, onChange: () -> Unit) {
        binding.spSecondaryFilter.isVisible = true
        binding.spSecondaryFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spSecondaryFilter.setSelection(selectedIndex)
        binding.spSecondaryFilter.onItemSelectedListener = SimpleItemSelectedListener(onChange)
    }

    protected fun hideSearch() {
        binding.searchContainer.isVisible = false
    }

    protected fun hidePrimaryFilter() {
        binding.spPrimaryFilter.isVisible = false
    }

    protected fun hideSecondaryFilter() {
        binding.spSecondaryFilter.isVisible = false
    }

    protected fun setPrimaryButton(label: String, listener: View.OnClickListener) {
        binding.btnPrimary.isVisible = true
        binding.btnPrimary.text = label
        binding.btnPrimary.setOnClickListener(listener)
    }

    protected fun setSecondaryButton(label: String, listener: View.OnClickListener) {
        binding.btnSecondary.isVisible = true
        binding.btnSecondary.text = label
        binding.btnSecondary.setOnClickListener(listener)
    }

    protected fun hidePrimaryButton() {
        binding.btnPrimary.isVisible = false
        binding.buttonRow.isVisible = binding.btnSecondary.isVisible
    }

    protected fun hideSecondaryButton() {
        binding.btnSecondary.isVisible = false
        binding.buttonRow.isVisible = binding.btnPrimary.isVisible
    }

    protected fun hideButtons() {
        binding.buttonRow.isVisible = false
    }

    protected fun searchText(): String = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

    protected fun primarySelection(): String = binding.spPrimaryFilter.selectedItem?.toString().orEmpty()
    protected fun secondarySelection(): String = binding.spSecondaryFilter.selectedItem?.toString().orEmpty()

    protected fun submitRows(rows: List<RowItem>) {
        rowAdapter.submitList(rows)
        binding.tvEmpty.isVisible = rows.isEmpty()
        binding.rvList.isVisible = rows.isNotEmpty()
    }

    protected open fun onSearchChanged() = Unit
    protected open fun onRowAction(item: RowItem) = Unit
    protected abstract fun onRowClick(item: RowItem)
}
