package dev.jasper.ishav2.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import dev.jasper.ishav2.data.formatMoney
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class BudgetViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.budget

    val data = store.data

    private val _month = MutableStateFlow(YearMonth.now())
    val month = _month.asStateFlow()

    val summary = combine(store.data, _month) { data, month -> BudgetStore.summarize(data, month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.summary())

    /** All-time totals, independent of the month being viewed. */
    val allTime = store.data.map { BudgetStore.allTimeOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.allTime())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (_month.value < YearMonth.now()) _month.value = _month.value.plusMonths(1)
    }

    fun add(type: TxType, amount: Double, category: String, note: String, daysAgo: Int) {
        if (amount <= 0) return
        viewModelScope.launch {
            val at = LocalDate.now().minusDays(daysAgo.toLong())
                .atTime(LocalTime.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val tx = Transaction(type = type, amount = amount, category = category, note = note.trim(), at = at)
            val alerts = store.add(tx)
            container.budgetAssistant.postAlerts(alerts)
            // Show the month the entry landed in (yesterday may be last month).
            _month.value = YearMonth.from(tx.date)
            _events.emit(
                if (type == TxType.EXPENSE) "Logged ${formatMoney(store.currency, amount)} · $category"
                else "Added ${formatMoney(store.currency, amount)} income"
            )
        }
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            store.delete(transaction.id)
            _events.emit("Deleted ${formatMoney(store.currency, transaction.amount)} · ${transaction.category}")
        }
    }

    fun setBudget(category: String, amount: Double) {
        viewModelScope.launch {
            store.setBudget(category, amount)
            _events.emit(
                if (amount > 0) "$category budget set to ${formatMoney(store.currency, amount)}"
                else "$category budget removed"
            )
        }
    }

    fun setCurrency(symbol: String) {
        viewModelScope.launch { store.setCurrency(symbol) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BudgetViewModel(container) }
        }
    }
}
