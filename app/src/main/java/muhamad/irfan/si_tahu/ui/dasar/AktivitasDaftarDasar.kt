package muhamad.irfan.si_tahu.ui.dasar

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.utama.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.ItemBaris

abstract class AktivitasDaftarDasar : AktivitasDasar() {
    protected lateinit var binding: ActivityListScreenBinding
    protected lateinit var rowAdapter: AdapterBarisUmum

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rowAdapter = AdapterBarisUmum(
            onItemClick = ::onRowClick,
            onActionClick = ::onRowAction,
            onEditClick = ::onRowEdit,
            onDeleteClick = ::onRowDelete
        )

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = rowAdapter
        binding.etSearch.addTextChangedListener { onSearchChanged() }

        binding.buttonRow.isVisible = false
        binding.fabAdd.isVisible = false
    }

    protected fun configureScreen(
        title: String,
        subtitle: String? = null,
        searchHint: String = "Cari data..."
    ) {
        bindToolbar(binding.toolbar, title, subtitle)
        binding.etSearch.hint = searchHint
    }

    protected fun setPrimaryFilter(
        options: List<String>,
        selectedIndex: Int = 0,
        onChange: () -> Unit
    ) {
        binding.spPrimaryFilter.isVisible = true
        binding.spPrimaryFilter.adapter = AdapterSpinner.stringAdapter(this, options)
        binding.spPrimaryFilter.setSelection(selectedIndex)
        binding.spPrimaryFilter.onItemSelectedListener = PendengarPilihItemSederhana(onChange)
    }

    protected fun setSecondaryFilter(
        options: List<String>,
        selectedIndex: Int = 0,
        onChange: () -> Unit
    ) {
        binding.spSecondaryFilter.isVisible = true
        binding.spSecondaryFilter.adapter = AdapterSpinner.stringAdapter(this, options)
        binding.spSecondaryFilter.setSelection(selectedIndex)
        binding.spSecondaryFilter.onItemSelectedListener = PendengarPilihItemSederhana(onChange)
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
        binding.buttonRow.isVisible = true
    }

    protected fun setSecondaryButton(label: String, listener: View.OnClickListener) {
        binding.btnSecondary.isVisible = true
        binding.btnSecondary.text = label
        binding.btnSecondary.setOnClickListener(listener)
        binding.buttonRow.isVisible = true
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

    protected fun searchText(): String {
        return binding.etSearch.text?.toString().orEmpty().trim().lowercase()
    }

    protected fun primarySelection(): String {
        return binding.spPrimaryFilter.selectedItem?.toString().orEmpty()
    }

    protected fun secondarySelection(): String {
        return binding.spSecondaryFilter.selectedItem?.toString().orEmpty()
    }

    protected fun submitRows(rows: List<ItemBaris>) {
        rowAdapter.submitList(rows)
        binding.tvEmpty.isVisible = rows.isEmpty()
        binding.rvList.isVisible = rows.isNotEmpty()
    }


    protected fun setFabAdd(listener: View.OnClickListener) {
        binding.fabAdd.isVisible = true
        binding.fabAdd.setOnClickListener(listener)
    }

    protected fun hideFabAdd() {
        binding.fabAdd.isVisible = false
    }
    protected open fun onRowEdit(item: ItemBaris) = Unit

    protected open fun onSearchChanged() = Unit
    protected open fun onRowAction(item: ItemBaris) = Unit
    protected open fun onRowDelete(item: ItemBaris) = Unit
    protected abstract fun onRowClick(item: ItemBaris)
}