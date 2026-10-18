package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.NotificationDao
import com.app.data.local.entity.NotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao
) : ViewModel() {

    val notifications: StateFlow<List<NotificationEntity>> = notificationDao.getAllNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    val unreadCount: StateFlow<Int> = notificationDao.getUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            notificationDao.deleteAll()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationDao.deleteById(id)
        }
    }
}
